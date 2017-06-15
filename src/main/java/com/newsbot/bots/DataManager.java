package com.newsbot.bots;

import com.sun.syndication.feed.synd.*;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.Search;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import java.net.URLEncoder;
import java.util.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;


public class DataManager {

    protected final EntityManagerFactory emf;
    public Map<String, String[]> feeds;

    public DataManager (EntityManagerFactory emf) {
        this.emf = emf;

        feeds = new HashMap<String, String[]>();
        // feeds.put("All", new String[]{"all", "url?feed_by=atom"});
        feeds.put("Big data", new String[]{"(data)|(analytics)", "http://www.dataversity.net/feed/?feed_by=atom"});
        feeds.put("Linux", new String[]{"linux", "http://www.technewsworld.com/perl/syndication/rssfull.pl?feed_by=atom"});
        feeds.put("Smartphones", new String[]{"Smartphones", "/rsstopfeeds.cms?feed_by=atom"});
        feeds.put("ios", new String[]{"ios", " https://9to5mac.com/feed/?feed_by=atom"});
        feeds.put("Security", new String[]{"security", "http://www.ibm.com/developerworks/views/security/rss/libraryview.jsp?feed_by=atom"});
        feeds.put("Web development", new String[]{"web", "http://www.ibm.com/developerworks/views/web/rss/libraryview.jsp?feed_by=atom"});
    }

    public void saveUser (User user) {
        user.setUpdateDate(new Date());
        EntityManager manager = emf.createEntityManager();
        try {
            manager.getTransaction().begin();
            if (user.getId() == 0) {
                manager.persist(user);
            } else {
                manager.merge(user);
            }
            manager.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace ();
        } finally {
            manager.close();
        }
    }

    public User getFbUser (String fbId) {
        EntityManager manager = emf.createEntityManager();
        try {
            Query query = manager.createQuery("select u from User u where u.fbId=:fbId");
            query.setParameter("fbId", fbId);

            List<User> users = query.getResultList();
            if (users == null || users.isEmpty()) {
                return null;
            } else {
                return users.get(0);
            }
        } finally {
            manager.close();
        }
    }

    public User getSlackUser (String slackChannel) {
        EntityManager manager = emf.createEntityManager();
        try {
            Query query = manager.createQuery("select u from User u where u.slackChannel=:slackChannel");
            query.setParameter("slackChannel", slackChannel);

            List<User> users = query.getResultList();
            if (users == null || users.isEmpty()) {
                return null;
            } else {
                return users.get(0);
            }
        } finally {
            manager.close();
        }
    }

    public List <User> getActiveUsers () {
        EntityManager manager = emf.createEntityManager();
        try {
            Query query = manager.createQuery("select u from User u where u.stopped=0");
            return query.getResultList();
        } finally {
            manager.close();
        }
    }

    public void saveToken (Token token) {
        token.setUpdateDate(new Date());
        EntityManager manager = emf.createEntityManager();
        try {
            manager.getTransaction().begin();
            if (token.getId() == 0) {
                manager.persist(token);
            } else {
                manager.merge(token);
            }
            manager.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace ();
        } finally {
            manager.close();
        }
    }

    public Token getToken (String teamId) {
        EntityManager manager = emf.createEntityManager();
        try {
            Query query = manager.createQuery("select u from Token u where u.teamId=:teamId");
            query.setParameter("teamId", teamId);

            List<Token> ts = query.getResultList();
            if (ts == null || ts.isEmpty()) {
                return null;
            } else {
                return ts.get(0);
            }
        } finally {
            manager.close();
        }
    }

    public void saveNewsItem (SyndEntry entry, String topic) {
        topic = topic.trim();

        String articleUrl = entry.getLink();
        String title = Util.cleanUp(entry.getTitle());
        String subtitle = Util.cleanUp(entry.getDescription().getValue());
        if (subtitle.length() > 4000) {
            subtitle = subtitle.substring(0, 4000);
        }

        EntityManager manager = emf.createEntityManager();
        try {
            // Query query = manager.createQuery("select ni from NewsItem ni where ni.articleUrl=:articleUrl and ni.topic=:topic");
            // query.setParameter("articleUrl", articleUrl);
            // NOTE: We do exact title match here since you could have different URLs for the same article in the same zone ...

            Query query = manager.createQuery("select ni from NewsItem ni where ni.title=:title and ni.subtitle=:subtitle and ni.topic=:topic");
            query.setParameter("title", title);
            query.setParameter("subtitle", subtitle);
            query.setParameter("topic", topic);
            List<NewsItem> nis = query.getResultList();
            if (nis == null || nis.isEmpty()) {
                // continue
                System.out.println("Saving: " + topic + " -- " + articleUrl);
            } else {
                return; // We will not save dup
            }

            NewsItem ni = new NewsItem ();
            ni.setTitle(title);
            ni.setSubtitle(subtitle);
            ni.setArticleUrl(articleUrl);
            ni.setImageUrl(""); // TODO: Fetch this stuff
            ni.setTopic(topic);
            ni.setUpdateDate(entry.getPublishedDate());
            try {
                // 10s timeout for the HTTP connection to fetch content
                Document doc = Jsoup.connect(articleUrl).timeout(10 * 1000).get();
                ni.setArticle(doc.text());
            } catch (Exception e) {
                e.printStackTrace();
                ni.setArticle("");
            }

            manager.getTransaction().begin();
            manager.persist(ni);
            manager.getTransaction().commit();

        } catch (Exception e) {
            e.printStackTrace ();
        } finally {
            manager.close();
        }
    }

    public List <NewsItem> getNewsItems (String topic) {
        EntityManager manager = emf.createEntityManager();
        try {
            Query query = manager.createQuery("select ni from NewsItem ni where ni.topic=:topic order by ni.updateDate desc");
            query.setParameter("topic", topic);
            List<NewsItem> items = query.getResultList();

            for (NewsItem item : items) {
                if (item.getImageUrl().isEmpty()) {
                    item.setImageUrl(feeds.get(item.getTopic())[2]);
                }
            }

            return items;
        } finally {
            manager.close();
        }
    }

    public List <NewsItem> searchNewsItems (String query) {
        List <NewsItem> result = new ArrayList <NewsItem> ();
        EntityManager manager = emf.createEntityManager();

        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(manager);
        try {
            // manager.getTransaction().begin();
            QueryBuilder qb = fullTextEntityManager.getSearchFactory()
                .buildQueryBuilder().forEntity(NewsItem.class).get();
            org.apache.lucene.search.Query luceneQuery = qb
                .keyword()
                .onFields("title", "subtitle", "article")
                .matching(query)
                .createQuery();

            // wrap Lucene query in a javax.persistence.Query
            javax.persistence.Query jpaQuery =
                fullTextEntityManager.createFullTextQuery(luceneQuery, NewsItem.class);

            // execute search
            result = jpaQuery.getResultList();

            for (NewsItem item : result) {
                if (item.getImageUrl().isEmpty()) {
                    item.setImageUrl(feeds.get(item.getTopic())[2]);
                }
            }
            // manager.getTransaction().commit();

        } catch (Exception e) {
            e.printStackTrace ();
        } finally {
            manager.close();
        }

        return result;
    }

    public NewsItem getNewsItem (long nid) {
        EntityManager manager = emf.createEntityManager();
        try {
            Query query = manager.createQuery("select ni from NewsItem ni where ni.id=:nid");
            query.setParameter("nid", nid);

            List<NewsItem> items = query.getResultList();
            if (items == null || items.isEmpty()) {
                return null;
            } else {
                NewsItem item = items.get(0);
                if (item.getImageUrl().isEmpty()) {
                    item.setImageUrl(feeds.get(item.getTopic())[2]);
                }
                return item;
            }
        } finally {
            manager.close();
        }
    }

    public static JSONObject replyFbItems (List <NewsItem> items, boolean all_topics) {
        Collections.sort(items);
        if (all_topics) {
            Set<String> topics = new HashSet <String> ();
            List <NewsItem> ns = new ArrayList <NewsItem> ();
            for (NewsItem item : items) {
                if (!topics.contains(item.getTopic())) {
                    topics.add(item.getTopic());
                    ns.add(item);
                }
            }
            for (NewsItem item : items) {
                if (!ns.contains(item)) {
                    ns.add(item);
                }
            }
            items = ns;
        }

        int limit = items.size();
        if (limit > 8) {
            limit = 8;
        }
        List <String> titles = new ArrayList <String> ();
        List <String> subtitles = new ArrayList <String> ();
        List <String> image_urls = new ArrayList <String> ();
        List <String> button_titles = new ArrayList <String> ();
        List <String> button_payloads = new ArrayList <String> ();
        for (int i = 0; i < limit; i++) {
            NewsItem item = items.get(i);
            titles.add(item.getTitle());
            subtitles.add(item.getSubtitle());
            image_urls.add(item.getImageUrl());
            button_titles.add("Read more");
            button_payloads.add("GET-SUMMARY-" + item.getId());
            button_titles.add("Hear it");
            button_payloads.add("https://fbdwbot.mybluemix.net/watson_speech?t=" + URLEncoder.encode(item.getSubtitle()));
            if (all_topics) {
                button_titles.add(item.getTopic());
                button_payloads.add("GET-TOPIC-" + item.getTopic());
            } else {
                button_titles.add("All my topics");
                button_payloads.add("NEWS");
            }
        }

        titles.add ("Actions");
        subtitles.add ("See more articles from this list or via searching. You can also change your interested topics.");
        image_urls.add ("http://dwnewsbot.ringfulhealth.com/dw/dw_setting.png");
        button_titles.add ("Next articles >");
        button_payloads.add ("NEXT-ARTICLE");
        button_titles.add ("Change my interests");
        button_payloads.add ("TOPICS");
        button_titles.add ("Search");
        button_payloads.add ("SEARCH");

        try {
            return com.ringfulhealth.chatbotbook.facebook.BaseServlet.createCarousel(titles, subtitles, image_urls, 3, button_titles, button_payloads);
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }

    }

    public static JSONArray replySlackItems (List <NewsItem> items, boolean all_topics) {
        Collections.sort(items);
        if (all_topics) {
            Set<String> topics = new HashSet <String> ();
            List <NewsItem> ns = new ArrayList <NewsItem> ();
            for (NewsItem item : items) {
                if (!topics.contains(item.getTopic())) {
                    topics.add(item.getTopic());
                    ns.add(item);
                }
            }
            for (NewsItem item : items) {
                if (!ns.contains(item)) {
                    ns.add(item);
                }
            }
            items = ns;
        }

        int limit = items.size();
        if (limit > 8) {
            limit = 8;
        }
        try {
            JSONArray a = new JSONArray();
            for (int i = 0; i < limit; i++) {
                NewsItem item = items.get(i);

                HashMap <String, String> buttons = new HashMap <String, String> ();
                // buttons.put("Hear it", "https://fbdwbot.mybluemix.net/watson_speech?t=" + URLEncoder.encode(item.getSubtitle()));
                buttons.put("Read more", "GET-SUMMARY-" + item.getId());
                if (all_topics) {
                    buttons.put(item.getTopic(), "GET-TOPIC-" + item.getTopic());
                } else {
                    buttons.put("All my topics", "NEWS");
                }
                JSONObject o = com.ringfulhealth.chatbotbook.slack.events.BaseServlet.createButtons(
                        item.getTitle(),
                        buttons
                );
                o.put("title_link", item.getArticleUrl());
                o.put("thumb_url", item.getImageUrl());
                o.put("unfurl_links", true);
                o.put("unfurl_media", true);
                o.put("text", "<https://fbdwbot.mybluemix.net/watson_speech?t=" + URLEncoder.encode(item.getSubtitle()) + "|Hear the summary>");
                a.put(o);
            }
            return a;

        } catch (Exception e) {
            e.printStackTrace();
            return new JSONArray();
        }

    }

}
