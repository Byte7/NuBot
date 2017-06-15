package com.newsbot.bots.slack;

import com.newsbot.bots.*;
import com.newsbot.botlib.slack.events.BaseServlet;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.persistence.EntityManagerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NewsServlet extends BaseServlet {

    public NewsServlet () {
        client_id = Constants.slack_client_id;
        client_secret = Constants.slack_client_secret;

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

    public void saveToken (String team_id, String token) {
        if (emf == null) {
            emf = (EntityManagerFactory) getServletContext().getAttribute("emf");
        }
        DataManager dm = new DataManager (emf);

        Token t = dm.getToken(team_id);
        if (t == null) {
            t = new Token ();
        }
        t.setTeamId(team_id);
        t.setBotToken(token);
        dm.saveToken(t);
    }

    public String findToken (String team_id) {
        if (emf == null) {
            emf = (EntityManagerFactory) getServletContext().getAttribute("emf");
        }
        DataManager dm = new DataManager (emf);

        Token t = dm.getToken(team_id);
        if (t != null) {
            return t.getBotToken();
        }
        return null;
    }

    public Object converse (String human, ConcurrentHashMap<String, Object> context) {
        System.out.println("nuBotSlackServlet converse: " + human);

        if (emf == null) {
            emf = (EntityManagerFactory) getServletContext().getAttribute("emf");
        }
        DataManager dm = new DataManager (emf);

        boolean new_user = false;
        User user = dm.getSlackUser((String) context.get("channel"));
        if (user == null) {
            user = new User ();
            user.setSlackId((String) context.get("sender_id"));
            user.setSlackTeamId((String) context.get("team_id"));
            user.setSlackChannel((String) context.get("channel"));
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
            List<String> allfaves = Arrays.asList("Big Data", "Android", "Artificial Intelligence", "iOS", "Linux", "Smartphones", "Laptops", "Cyber Security", "Web Development");
            Random rand = new Random ();
            for (int i = 0; i < 3; i++) {
                int index = rand.nextInt(allfaves.size());
                faves.add(allfaves.get(index));
                allfaves.remove(index);
            }

            user.setFaves(String.join(",", faves));
            dm.saveUser(user);

            try {
                return createButtons(
                    "Great, I selected the following topics for you: \"" + user.getFaves() + "\".",
                    new HashMap<String, String>(){{
                        put("I want to change", "TOPICS");
                        put("Great, show me!", "NEWS");
                    }}
                );
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
            replies.add(dm.replySlackItems(items, true));
            return replies;
        }

        boolean change_faves = human.equalsIgnoreCase("topics");

        if (faves == null || faves.isEmpty() || change_faves || new_user) {
            if (new_user) {
                // New user
                List replies = new ArrayList();
                replies.add("Hi! " + user.getFirst_name() + "! nuBot delivers news and articles from the latest tech trends around the globe to you!");
                replies.add("To start, please reply with your interests in technical topics. Example:");
                replies.add("Big Data, Android, Artificial Intelligence, iOS, Linux, Smartphones, Laptops, Cyber Security, Web Development");
                return replies;

            } else if (change_faves) {
                // The human input asks for changing faves
                user.setFaves("");
                dm.saveUser(user);

                List replies = new ArrayList();
                 replies.add("Please reply with your interests in technical topics. Example:");
                replies.add("Big Data, Android, Artificial Intelligence, iOS, Linux, Smartphones, Laptops, Cyber Security, Web Development");
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
                    replies.add("Big Data, Android, Artificial Intelligence, iOS, Linux, Smartphones, Laptops, Cyber Security, Web Development");
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
                        return createButtons(
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
            replies.add(dm.replySlackItems(items, false));
            return replies;
        }

        System.out.println("CHECK GET SUMMARY");
        if (human.startsWith("GET-SUMMARY-")) {
            long sid = Long.parseLong(human.substring(12));
            final NewsItem ni = dm.getNewsItem(sid); // Needed this for access from inner class for the HashMap init

            List replies = new ArrayList();
            try {
                replies.add(createButtons(
                        ni.getTitle(),
                        new HashMap<String, String>(){{
                            put(ni.getTopic(), "GET-TOPIC-" + ni.getTopic());
                            put("All my topics", "NEWS");
                        }}
                ).put("title_link", ni.getArticleUrl()).put("text", ni.getSubtitle()));
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
            replies.add(dm.replySlackItems(items, true));
            return replies;

        }

        System.out.println("CHECK NEXT ARTICLE");
        if (human.equals("NEXT-ARTICLE")) {
            List <NewsItem> items = (List <NewsItem>) context.get("items");
            if (items == null || items.isEmpty()) {
                try {
                    return createButtons(
                            "Sorry, No more articles.",
                            new HashMap<String, String>(){{
                                put("Search", "SEARCH");
                                put("Articles for me", "NEWS");
                            }}
                    );
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
                    replies.add(dm.replySlackItems(items, false));
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
                return createButtons(
                        "Hello! It's great to meet you! Would you like to see",
                        new HashMap<String, String>(){{
                            put("Search", "SEARCH");
                            put("Articles for me", "NEWS");
                            put("Change my interests", "TOPICS");
                        }}
                );
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if ("TOPIC".equalsIgnoreCase(cstr)) {
            try {
                return createButtons(
                        "Are you sure you want to update your interested topics?",
                        new HashMap<String, String>(){{
                            put("Yes", "TOPICS");
                        }}
                );
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if ("STOP".equalsIgnoreCase(cstr)) {
            try {
                return createButtons(
                        "Are you sure you want to stop nuBot news delivery to you?",
                        new HashMap<String, String>(){{
                            put("Yes", "STOP");
                        }}
                );
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if ("RESUME".equalsIgnoreCase(cstr)) {
            try {
                return createButtons(
                        "Are you sure you want to resume nuBot news delivery to you?",
                        new HashMap<String, String>(){{
                            put("Yes", "RESUME");
                        }}
                );
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if ("SEARCH".equalsIgnoreCase(cstr)) {
            context.put("search", true);
            return "Please enter your search query here. For example, you can enter \"Java Stream\"";

        } else if ("MORE".equalsIgnoreCase(cstr)) {
            try {
                return createButtons(
                        "Do you want to see more articles from the previous list?",
                        new HashMap<String, String>(){{
                            put("Yes", "NEXT-ARTICLE");
                            put("Search", "SEARCH");
                            put("Help", "HELP");
                        }}
                );
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            try {
                return createButtons(
                        "Sorry, I don't understand you.",
                        new HashMap<String, String>(){{
                            put("Articles for me", "NEWS");
                            put("Search", "SEARCH");
                            put("Help me", "HELP");
                        }}
                );
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
        replies.add(dm.replySlackItems(items, true));
        return replies;
    }

}
