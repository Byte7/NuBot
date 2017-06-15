package com.newsbot.bots.facebook;

import com.newsbot.bots.Constants;
import com.newsbot.bots.DataManager;
import com.newsbot.bots.NewsItem;
import com.newsbot.bots.User;
import com.newsbot.botlib.facebook.BaseServlet;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.persistence.EntityManagerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NewsServlet extends BaseServlet {

    public NewsServlet () {
        page_access_token = Constants.fb_page_access_token;

        zencoder_apikey = Constants.zencoder_apikey;
        audio_bluemix_username = Constants.audio_bluemix_username;
        audio_bluemix_password = Constants.audio_bluemix_password;

        nlp_bluemix_username = Constants.nlp_bluemix_username;
        nlp_bluemix_password = Constants.nlp_bluemix_password;
        nlp_bluemix_id = Constants.nlp_bluemix_id;

        conv_bluemix_username = Constants.conv_bluemix_username;
        conv_bluemix_password = Constants.conv_bluemix_password;
        conv_bluemix_id = Constants.conv_bluemix_id;
    }

    private EntityManagerFactory emf;

    public Object converse (String human, ConcurrentHashMap<String, Object> context) {
        System.out.println("nuBotFBServlet converse: " + human);

        if (emf == null) {
            // This is for Tomcat
            emf = (EntityManagerFactory) getServletContext().getAttribute("emf");
        }
        DataManager dm = new DataManager (emf);

        boolean new_user = false;
        User user = dm.getFbUser((String) context.get("sender_id"));
        if (user == null) {
            user = new User ();
            user.setFbId((String) context.get("sender_id"));

            HashMap profile = getUserProfile(user.getFbId());
            if (profile != null && !profile.isEmpty()) {
                user.setFirst_name((String) profile.get("first_name"));
                user.setLast_name((String) profile.get("last_name"));
                user.setProfile_pic((String) profile.get("profile_pic"));
                user.setLocale((String) profile.get("locale"));
                user.setGender((String) profile.get("gender"));
                try {
                    user.setTimezone((Integer) profile.get("timezone"));
                } catch (Exception e) {
                    // This one does not exist
                    user.setTimezone(0);
                }
            }

            dm.saveUser(user);
            new_user = true;
        }
        List <String> faves = user.getFavesList();

        if (human.equalsIgnoreCase("stop")) {
            user.setStopped(1);
            dm.saveUser(user);
            context.remove("search");
            return "I have stopped your news delivery. You can still get nuBot news by initiating a conversation with me.";
        }

        if (human.equalsIgnoreCase("resume")) {
            user.setStopped(0);
            dm.saveUser(user);
            context.remove("search");
            return "I have resumed your news delivery.";
        }

        if (human.equalsIgnoreCase("RANDOM-TOPICS")) {
            faves = new ArrayList<String>();
            List<String> allfaves = Arrays.asList("Big Data", "iOS", "Linux", "Smartphones", "Security", "Web Development");
            Random rand = new Random ();
            for (int i = 0; i < 3; i++) {
                int index = rand.nextInt(allfaves.size());
                faves.add(allfaves.get(index));
                allfaves.remove(index);
            }

            user.setFaves(String.join(",", faves));
            dm.saveUser(user);

            try {
                JSONObject payload = new JSONObject();
                payload.put("template_type", "button");
                payload.put("text", "Great, I selected the following topics for you: \"" + user.getFaves() + "\".");
                String[] button_titles = {"I want to change my topics", "Awesome, show my new found interests!!!"};
                String[] button_payloads = {"TOPICS", "NEWS"};
                payload.put("buttons", createButtons(button_titles, button_payloads));
                return payload;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (context.get("search") != null) {
            List <NewsItem> items = dm.searchNewsItems(human);
            context.put("items", items);
            context.remove("search");

            List replies = new ArrayList();
            replies.add ("Search results for: " + human);
            replies.add(dm.replyFbItems(items, true));
            return replies;
        }

        boolean change_faves = human.equalsIgnoreCase("topics");

        if (faves == null || faves.isEmpty() || change_faves || new_user) {
            if (new_user) {
                // New user
                List replies = new ArrayList();
                replies.add("Hi! " + user.getFirst_name() + "! nuBot delivers news and articles from the latest tech trends around the globe to you!");
                replies.add("To start, please reply with your interests in technical topics. Example:");
                replies.add("Big Data, iOS, Linux, Smartphones, Security, Web Development");
                return replies;

            } else if (change_faves) {
                // The human input asks for changing faves
                user.setFaves("");
                dm.saveUser(user);

                List replies = new ArrayList();
                replies.add("Please reply with your interests in technical topics. Example:");
                replies.add("Big Data, iOS, Linux, Smartphones, Security, Web Development");
                return replies;

            } else {
                // The human is no longer asking for changing faves. He is giving his faves
                Set <String> faveset = new HashSet <String> ();
                Iterator it = dm.feeds.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry <String, String[]> pair = (Map.Entry)it.next();
                    System.out.println(pair.getKey() + " = " + pair.getValue());

                    Pattern ptn2 = Pattern.compile(pair.getValue()[0], Pattern.CASE_INSENSITIVE);
                    Matcher match2 = ptn2.matcher(human);
                    if (match2.find()) {
                        faveset.add(pair.getKey());
                    }
                }
                faves = new ArrayList <String> (faveset);

                if (faves.isEmpty()) {
                    List replies = new ArrayList();
                    replies.add("Sorry, I do not see any available technical topic.");
                    replies.add("Please reply with your interests in technical topics. Example:");
                    replies.add("Big Data, iOS, Linux, Smartphones, Security, Web Development");
                    try {
                        replies.add(createButtons(
                                "Or, alternatively, you can let me randomly pick 3 topics for you!",
                                new HashMap<String, String>(){{
                                    put("Choose random", "RANDOM-TOPICS");
                                }}
                        ));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return replies;

                } else {
                    user.setFaves(String.join(",", faves));
                    dm.saveUser(user);

                    try {
                        return createQuickReplies(
                                "Great, your topics are \"" + user.getFaves() + "\".",
                                new HashMap<String, String>() {{
                                    put("Oh no", "TOPICS");
                                    put("Okay!", "NEWS");
                                }}
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        System.out.println("CHECK GET TOPIC");
        if (human.startsWith("GET-TOPIC-")) {
            String topic = human.substring(10);
            List <NewsItem> items = dm.getNewsItems(topic);
            context.put("items", items);

            List replies = new ArrayList();
            replies.add("Latest articles from " + topic);
            replies.add(dm.replyFbItems(items, false));
            return replies;
        }

        System.out.println("CHECK GET SUMMARY");
        if (human.startsWith("GET-SUMMARY-")) {
            long sid = Long.parseLong(human.substring(12));
            final NewsItem ni = dm.getNewsItem(sid); // Needed this for access from inner class for the HashMap init

            List replies = new ArrayList();
            try {
                List <String> ss = splitByWord(ni.getSubtitle(), 315);
                for (String s : ss) {
                    replies.add(s);
                }

                replies.add(createButtons(
                        "What's next?",
                        new HashMap<String, String>(){{
                            put("Read article", ni.getArticleUrl());
                            put(ni.getTopic(), "GET-TOPIC-" + ni.getTopic());
                            put("All my topics", "NEWS");
                        }}
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }

            return replies;
        }

        System.out.println("CHECK NEWS");
        if (human.equalsIgnoreCase("news")) {
            // Show favourite topics
            List <NewsItem> items = new ArrayList <NewsItem> ();
            if (!faves.contains("nuBot Blog")) {
                faves.add("nuBot Blog");
            }
            for (String fave : faves) {
                List <NewsItem> nis = dm.getNewsItems(fave);
                if (nis == null || nis.isEmpty()) {
                    continue;
                }
                // For not adding duplicate articles. Just adding each article once -> based on the first topic found
                for (NewsItem ni : nis) {
                    boolean needToAdd = true;
                    for (NewsItem item : items) {
                        if (ni.getTitle().equals(item.getTitle())) {
                            needToAdd = false;
                            break;
                        }
                    }
                    if (needToAdd) {
                        items.add(ni);
                    }
                }
            }
            context.put("items", items);

            List replies = new ArrayList();
            replies.add ("Latest articles from topics you are interested in.");
            replies.add(dm.replyFbItems(items, true));
            return replies;

        }

        System.out.println("CHECK NEXT ARTICLE");
        if (human.equals("NEXT-ARTICLE")) {
            List <NewsItem> items = (List <NewsItem>) context.get("items");
            if (items == null || items.isEmpty()) {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("template_type", "button");
                    payload.put("text", "Sorry, No more articles.");
                    String[] button_titles = {"Articles for me", "Search"};
                    String[] button_payloads = {"NEWS", "SEARCH"};
                    payload.put("buttons", createButtons(button_titles, button_payloads));
                    return payload;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return "Sorry, No more articles";

            } else {
                int limit = items.size();
                if (limit > 30) {
                    limit = 30;
                }
                for (int i = 0; i < limit; i++) {
                    items.remove(0);
                }
                context.put("items", items);

                if (items.isEmpty()) {
                    return "You have reached the end of the list!";
                } else {
                    List replies = new ArrayList();
                    replies.add("More articles:");
                    replies.add(dm.replyFbItems(items, false));
                    return replies;
                }
            }
        }

        System.out.println("CHECK NLP");
        String cstr = "";
        JSONObject json = watsonConversation(human);
        if (json != null) {
            try {
                JSONArray intents = json.getJSONArray("intents");
                if (intents.getJSONObject(0).getDouble("confidence") > 0.5) {
                    cstr = intents.getJSONObject(0).getString("intent");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("DETECTED INTENT: " + cstr);

        if ("HELP".equalsIgnoreCase(cstr)) {
            return "Hello! Here are a couple of commands to get you started: NEWS to get latest articles. TOPICS to change your interested topics; STOP to stop news delivery; and RESUME to resume news delivery.";

        } else if ("HELLO".equalsIgnoreCase(cstr)) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("template_type", "button");
                payload.put("text", "Hello! Nice meeting you! Would you like to see");
                String[] button_titles = {"Articles for me", "Change my interests", "Search"};
                String[] button_payloads = {"NEWS", "TOPICS", "SEARCH"};
                payload.put("buttons", createButtons(button_titles, button_payloads));
                return payload;
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if ("TOPIC".equalsIgnoreCase(cstr)) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("template_type", "button");
                payload.put("text", "Are you sure you want to update your interested topics?");
                String[] button_titles = {"Yes"};
                String[] button_payloads = {"TOPICS"};
                payload.put("buttons", createButtons(button_titles, button_payloads));
                return payload;
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if ("STOP".equalsIgnoreCase(cstr)) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("template_type", "button");
                payload.put("text", "Are you sure you want to stop nuBot news delivery to you?");
                String[] button_titles = {"Yes"};
                String[] button_payloads = {"STOP"};
                payload.put("buttons", createButtons(button_titles, button_payloads));
                return payload;
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if ("RESUME".equalsIgnoreCase(cstr)) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("template_type", "button");
                payload.put("text", "Are you sure you want to resume nuBot news delivery to you?");
                String[] button_titles = {"Yes"};
                String[] button_payloads = {"RESUME"};
                payload.put("buttons", createButtons(button_titles, button_payloads));
                return payload;
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if ("SEARCH".equalsIgnoreCase(cstr)) {
            context.put("search", true);
            return "Please enter your search query here. For example, you can enter \"Java Stream\"";

        } else if ("MORE".equalsIgnoreCase(cstr)) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("template_type", "button");
                payload.put("text", "Do you want to see more articles from the previous list?");
                String[] button_titles = {"Yes", "Search", "Help"};
                String[] button_payloads = {"NEXT-ARTICLE", "SEARCH", "HELP"};
                payload.put("buttons", createButtons(button_titles, button_payloads));
                return payload;
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            try {
                JSONObject payload = new JSONObject();
                payload.put("template_type", "button");
                payload.put("text", "Sorry, I don't understand you.");
                String[] button_titles = {"Help me", "Articles for me", "Search"};
                String[] button_payloads = {"HELP", "NEWS", "SEARCH"};
                payload.put("buttons", createButtons(button_titles, button_payloads));
                return payload;
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

        return "";
    }


    public List showNews (String greeting, List <String> faves, DataManager dm) {
        List <NewsItem> items = new ArrayList <NewsItem> ();
        if (!faves.contains("nuBot Blog")) {
            faves.add("nuBot Blog");
        }
        for (String fave : faves) {
            items.addAll(dm.getNewsItems(fave));
        }
        List replies = new ArrayList();
        if (greeting.isEmpty()) {
            // Nothing
        } else {
            replies.add (greeting);
        }
        replies.add(dm.replyFbItems(items, true));
        return replies;
    }

}
