package hudson.plugins.campfire;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import hudson.model.Hudson;
import hudson.ProxyConfiguration;

public class Campfire {
    private String subdomain;
    private String token;
    private boolean ssl;

    public Campfire(String subdomain, String token, boolean ssl) {
        super();
        this.subdomain = subdomain;
        this.token = token;
        this.ssl = ssl;
    }

    protected HttpClient getClient() {
        Credentials defaultcreds = new UsernamePasswordCredentials(this.token, "x");
        CredentialsProvider cp = new BasicCredentialsProvider();
        cp.setCredentials(new AuthScope(getHost(), -1, AuthScope.ANY_REALM), defaultcreds);
        HttpClientBuilder clientBuilder = HttpClients.custom()
                .setDefaultCredentialsProvider(cp)
                .setRedirectStrategy(new LaxRedirectStrategy());

        ProxyConfiguration proxy = Hudson.getInstance().proxy;
        if (proxy != null) {
            clientBuilder.setProxy(new HttpHost(proxy.name, proxy.port));
        }

        return clientBuilder.build();
    }

    protected String getHost() {
      return this.subdomain + ".campfirenow.com";
    }

    public String getSubdomain() {
      return this.subdomain;
    }

    public String getToken() {
      return this.token;
    }

    protected String getProtocol() {
      if (this.ssl) { return "https://"; }
      return "http://";
    }

    public int post(String url, String body) {
        HttpPost post = new HttpPost(getProtocol() + getHost() + "/" + url);
        post.setHeader("Content-Type", "application/xml");
        post.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_4; en-us) AppleWebKit/533.16 (KHTML, like Gecko) Version/5.0 Safari/533.16");
        try {
            post.setEntity(new StringEntity(body, "application/xml", "UTF8"));
            HttpResponse resp = getClient().execute(post);
            return resp.getStatusLine().getStatusCode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            post.releaseConnection();
        }
    }

    public String get(String url) {
        HttpGet get = new HttpGet(getProtocol() + getHost() + "/" + url);
        get.setHeader("Content-Type", "application/xml");
        get.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_4; en-us) AppleWebKit/533.16 (KHTML, like Gecko) Version/5.0 Safari/533.16");
        try {
            HttpResponse response = getClient().execute(get);
            verify(response.getStatusLine().getStatusCode());
            return EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            get.releaseConnection();
        }
    }

    public boolean verify(int returnCode) {
        if (returnCode != 200) {
            throw new RuntimeException("Unexpected response code: " + Integer.toString(returnCode));
        }
        return true;
    }

    private List<Room> getRooms(){
        String body = get("rooms.xml");

        List<Room> rooms;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            StringReader reader = new StringReader(body);
            InputSource inputSource = new InputSource( reader );
            Document doc = builder.parse(inputSource);

            XPath xpath = XPathFactory.newInstance().newXPath();
            XPathExpression roomExpr = xpath.compile("//room");
            XPathExpression nameExpr = xpath.compile(".//name");
            XPathExpression idExpr = xpath.compile(".//id");

            NodeList roomNodeList = (NodeList) roomExpr.evaluate(doc, XPathConstants.NODESET);
            rooms = new ArrayList<Room>();
            for (int i = 0; i < roomNodeList.getLength(); i++) {
                Node roomNode = roomNodeList.item(i);
                String name = ((NodeList) nameExpr.evaluate(roomNode, XPathConstants.NODESET)).item(0).getFirstChild().getNodeValue();
                String id = ((NodeList) idExpr.evaluate(roomNode, XPathConstants.NODESET)).item(0).getFirstChild().getNodeValue();
                rooms.add(new Room(this, name.trim(), id.trim()));
            }
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
        return rooms;
    }

    public Room findRoomByName(String name) {
        for (Room room : getRooms()) {
            if (room.getName().equals(name)) {
                return room;
            }
        }
        return null;
    }

    private Room createRoom(String name) {
        verify(post("rooms.xml", "<request><room><name>" + name + "</name><topic></topic></room></request>"));
        return findRoomByName(name);
    }

    public Room findOrCreateRoomByName(String name) {
        Room room = findRoomByName(name);
        if (room != null) {
            return room;
        }
        return createRoom(name);
    }
}
