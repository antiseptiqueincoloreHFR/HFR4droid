package info.toyonos.hfr4droid.common.core.data;

import info.toyonos.hfr4droid.common.HFR4droidApplication;
import info.toyonos.hfr4droid.common.R;
import info.toyonos.hfr4droid.common.core.auth.HFRAuthentication;
import info.toyonos.hfr4droid.common.core.bean.AlertQualitay;
import info.toyonos.hfr4droid.common.core.bean.Category;
import info.toyonos.hfr4droid.common.core.bean.Post;
import info.toyonos.hfr4droid.common.core.bean.PostFromSearch;
import info.toyonos.hfr4droid.common.core.bean.Profile;
import info.toyonos.hfr4droid.common.core.bean.Profile.Gender;
import info.toyonos.hfr4droid.common.core.bean.Profile.ProfileType;
import info.toyonos.hfr4droid.common.core.bean.SubCategory;
import info.toyonos.hfr4droid.common.core.bean.Topic;
import info.toyonos.hfr4droid.common.core.bean.Topic.TopicStatus;
import info.toyonos.hfr4droid.common.core.bean.Topic.TopicType;
import info.toyonos.hfr4droid.common.core.utils.HttpClient;
import info.toyonos.hfr4droid.common.core.utils.HttpClientHelper;
import info.toyonos.hfr4droid.common.core.utils.TransformStreamException;
import info.toyonos.hfr4droid.common.service.MpNotifyService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.Intent;
import android.text.Html;
import android.util.Log;

/**
 * <p>Implémentation pour le forum de Hardware.fr du <code>MDDataRetriever</code></p>
 * 
 * @author ToYonos
 * @see info.toyonos.core.data.MDDataRetriever
 *
 */
public class HFRDataRetriever implements MDDataRetriever
{
	private static final String CATS_CACHE_FILE_NAME = "hfr4droid_cats.dat";
	
	public static final String BASE_URL			= "https://forum.hardware.fr";
	//public static final String BASE_URL		= "https://192.168.1.2/hfr-dev";
	public static final String IMG_URL			= "https://forum-images.hardware.fr";

	public static final String CATS_URL			= BASE_URL + "/";
	public static final String SUBCATS_URL		= BASE_URL + "/message.php?&config=hfr.inc&cat={$cat}";
	public static final String TOPICS_URL		= BASE_URL + "/forum1.php?config=hfr.inc&cat={$cat}&subcat={$subcat}&page={$page}&owntopic={$type}";
	public static final String ALL_TOPICS_URL	= BASE_URL + "/forum1f.php?config=hfr.inc&owntopic={$type}";
	public static final String POSTS_URL		= BASE_URL + "/forum2.php?config=hfr.inc&cat={$cat}&post={$topic}&page={$page}";
	public static final String SEARCH_POSTS_URL	= BASE_URL + "/forum2.php?config=hfr.inc&cat={$cat}&post={$topic}&word={$word}&spseudo={$pseudo}&currentnum={$fp}&filter=1";
	public static final String SMILIES_URL		= BASE_URL + "/message-smi-mp-aj.php?config=hfr.inc&findsmilies={$tag}";
	public static final String QUOTE_URL		= BASE_URL + "/message.php?config=hfr.inc&cat={$cat}&post={$topic}&numrep={$post}";
	public static final String EDIT_URL			= BASE_URL + "/message.php?config=hfr.inc&cat={$cat}&post={$topic}&numreponse={$post}";
	public static final String KEYWORDS_URL		= BASE_URL + "/wikismilies.php?config=hfr.inc&detail={$code}";
	public static final String MPS_URL			= BASE_URL + "/forum1.php?config=hfr.inc&cat=1&page=500000&owntopic=0";
	public static final String PROFILE_URL		= BASE_URL + "/profilebdd.php?config=hfr.inc&pseudo={$pseudo}";

	public static final String IMG_PERSO_URL	= IMG_URL + "/images/perso/";
	
	public static final String AQ_BY_TOPIC_URL	= "https://alerte-qualitay.toyonos.info/api/getAlertesByTopic.php5?topic_id={$topic}";
	
	public static final String MAINTENANCE 		= "Serveur en cours de maintenance. <br /><br />Veuillez nous excuser pour la gêne occasionnée";
	public static final String TOPIC_DELETED	= "Désolé, ce sujet n'existe pas";
	
	public static final String FAKE_ACCOUNT_USER = "hfr4droid";
	public static final String FAKE_ACCOUNT_MD5_PASS = "57e2d4c435b8aeea182d5126be1c46b4";
	
	private HFR4droidApplication context;
	private HttpClientHelper httpClientHelper;
	private HFRAuthentication auth;
	
	private String hashCheck;
	private Map<Category, List<SubCategory>> cats;
	private CookieStore fakeCs = null;

	public HFRDataRetriever(HFR4droidApplication context, HttpClientHelper httpClientHelper)
	{
		this(context, httpClientHelper, null, false, false);
	}
	
	public HFRDataRetriever(HFR4droidApplication context, HttpClientHelper httpClientHelper, boolean clearCache)
	{
		this(context, httpClientHelper, null, clearCache, false);
	}

	public HFRDataRetriever(HFR4droidApplication context, HttpClientHelper httpClientHelper, HFRAuthentication auth, boolean clearCache, boolean prefCredentialsOk)
	{
		this.context = context;
		this.auth = auth;
		this.httpClientHelper = httpClientHelper;
		
		hashCheck = null;
		cats = null;
		if (clearCache) clearCache();
		
		if(auth != null)
		{
			fakeCs = new BasicCookieStore();
			GregorianCalendar calendar = new GregorianCalendar();
			calendar.add(Calendar.YEAR, 10);
			BasicClientCookie user = new BasicClientCookie("md_user", 
				context.isPreloadingMultiSet() && prefCredentialsOk ? 
					context.getPreloadingPseudo() :
					FAKE_ACCOUNT_USER);
			user.setDomain(".hardware.fr");
			user.setExpiryDate(calendar.getTime());
			user.setPath("/");
			BasicClientCookie pass = new BasicClientCookie("md_passs",
				context.isPreloadingMultiSet() && prefCredentialsOk ? 
					getMd5(context.getPreloadingPassword()) :
					FAKE_ACCOUNT_MD5_PASS);
			pass.setDomain(".hardware.fr");
			pass.setExpiryDate(calendar.getTime());
			pass.setPath("/");
			fakeCs.addCookie(user);
			fakeCs.addCookie(pass);
		}
	}
	
	private String getMd5(String password)
	{
		MessageDigest md;
		try
		{
			md = MessageDigest.getInstance("MD5");
			byte[] bytes = md.digest(password.getBytes());
			StringBuffer formattedPassword = new StringBuffer();
		    for (int i = 0; i < bytes.length; i++) formattedPassword.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
			return formattedPassword.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			Log.e(HFR4droidApplication.TAG, e.getMessage(), e);
			return "";
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public String getHashCheck() throws DataRetrieverException
	{
		if (hashCheck == null)
		{
			// Dans ce cas on va le lire sur la page principale du forum (la page des cats)
			String content = null;
			try
			{
				content = getAsString(CATS_URL);
				if (content == null) return null;
			}
			catch (Exception e)
			{
				throw new DataRetrieverException(context.getString(R.string.error_dr_hash_check), e);
			}
			hashCheck = getSingleElement("<input\\s*type=\"hidden\"\\s*name=\"hash_check\"\\s*value=\"(.+?)\" />", content);
		}
		return hashCheck;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getBaseUrl()
	{
		return BASE_URL;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getImgPersoUrl()
	{
		return IMG_PERSO_URL;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public List<Category> getCats() throws DataRetrieverException
	{	
		ArrayList<Category> tmpCats = new ArrayList<Category>();

		// Cat des messages privés
		Category mpCat = new Category(Category.MPS_CAT);
		tmpCats.add(mpCat);

		if (this.cats == null && !deserializeCats())
		{
			String content = null;
			try
			{
				content = getAsString(CATS_URL);
				if (content == null) return null;
			}
			catch (Exception e)
			{
				throw new DataRetrieverException(context.getString(R.string.error_dr_cats), e);
			}

			this.cats = new LinkedHashMap<Category, List<SubCategory>>();
			
			// Cat des modals
			Pattern p = Pattern.compile("<a\\s*class=\"cCatTopic\"\\s*href=\"/forum1\\.php\\?config=hfr\\.inc&amp;cat=0&amp;"
				, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
			Matcher m = p.matcher(content);
			if  (m.find())
			{
				this.cats.put(Category.MODO_CAT, null);
			}
			
			p = Pattern.compile(
				"<tr.*?id=\"cat([0-9]+)\".*?" +
				"<td.*?class=\"catCase1\".*?<b><a\\s*href=\"/hfr/([a-zA-Z0-9-]+)/.*?\"\\s*class=\"cCatTopic\">(.+?)</a></b>.*?" +
				"</tr>"
				, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
			m = p.matcher(content);
			while (m.find())
			{
				Category newCat = new Category(Integer.parseInt(m.group(1)), m.group(2), m.group(3));
				this.cats.put(newCat, null);
			}
			Log.d(HFR4droidApplication.TAG, "New cats retrieved, let's serialize them...");
			serializeCats();
		}
		
		tmpCats.addAll(this.cats.keySet());
		
		// Cat représentant "toutes les cats"
		tmpCats.add(tmpCats.size() > 1 && tmpCats.get(1).equals(Category.MODO_CAT) ? 2 : 1, Category.ALL_CATS);

		return tmpCats;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Category getCatByCode(String code) throws DataRetrieverException
	{
		if (code == null) return null;
		
		if (cats == null) getCats();
		for (Category cat : cats.keySet())
		{
			if (code.equals(cat.getCode())) return cat;
		}
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Category getCatById(long id) throws DataRetrieverException
	{		
		if (cats == null) getCats();
		for (Category cat : cats.keySet())
		{
			if (id == cat.getId()) return cat;
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public List<SubCategory> getSubCats(Category cat) throws DataRetrieverException
	{
		Category keyCat = getCatById(cat.getId());
		if (keyCat != null)
		{
			List<SubCategory> currentSubCats = cats.get(keyCat);
			if (currentSubCats == null)
			{
				currentSubCats = new ArrayList<SubCategory>();
				String content = null;
				try
				{
					String url = SUBCATS_URL.replaceFirst("\\{\\$cat\\}", keyCat.getRealId());
					content = getAsString(url);
					if (content == null) return null;
				}
				catch (Exception e)
				{
					throw new DataRetrieverException(context.getString(R.string.error_dr_subcats), e);
				}

				Pattern p = Pattern.compile("<option\\s*value=\"([0-9]+)\"\\s*>(.+?)</option>"
					, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
				Matcher m = p.matcher(content);
				while (m.find())
				{
					SubCategory subCat = new SubCategory(keyCat, Integer.parseInt(m.group(1)), m.group(2));
					currentSubCats.add(subCat);
				}
				cats.put(keyCat, currentSubCats);
				Log.d(HFR4droidApplication.TAG, "New subcats retrieved (from " + keyCat.toString() + "), let's serialize them...");
				serializeCats();
			}
			return currentSubCats;
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isSubCatsLoaded(Category cat) throws DataRetrieverException
	{
		Category keyCat = getCatById(cat.getId());
		return keyCat != null && cats.get(keyCat) != null;
	}

	/**
	 * {@inheritDoc}
	 */
	public SubCategory getSubCatById(Category cat, long id) throws DataRetrieverException
	{		
		if (cats == null) throw new DataRetrieverException(context.getString(R.string.no_cats_cache));

		Category keyCat = getCatById(cat.getId());
		if (cats.get(keyCat) == null) throw new DataRetrieverException(context.getString(R.string.no_subcat_cache, keyCat.toString()));
		for (SubCategory subCat : cats.get(keyCat))
		{
			if (subCat.getSubCatId() == id) return subCat;
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public List<Topic> getTopics(Category cat, TopicType type) throws DataRetrieverException
	{
		return getTopics(cat, type, 1);
	}

	/**
	 * {@inheritDoc}
	 */
	public List<Topic> getTopics(Category cat, TopicType type, int pageNumber) throws DataRetrieverException
	{
		String url = null;
		if (cat.equals(Category.ALL_CATS))
		{
			url = ALL_TOPICS_URL.replaceFirst("\\{\\$type\\}", String.valueOf(type.getValue()));
		}
		else
		{
			String subCatId = cat.getSubCatId() != -1 ? String.valueOf(cat.getSubCatId()) : "";
			url = TOPICS_URL.replaceFirst("\\{\\$cat\\}", cat.getRealId())
			.replaceFirst("\\{\\$subcat\\}", subCatId)
			.replaceFirst("\\{\\$page\\}", String.valueOf(pageNumber))
			.replaceFirst("\\{\\$type\\}", String.valueOf(type.getValue()));
		}
		return innerGetTopics(cat, url);
	}

	private List<Topic> innerGetTopics(Category cat, String url) throws DataRetrieverException
	{
		ArrayList<Topic> topics = new ArrayList<Topic>();
		String content = null;
		try
		{
			content = getAsString(url);
			if (content == null) return null;
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_topics), e);
		}

        Pattern p = Pattern.compile(
        	"(?:(?:<th\\s*class=\"padding\".*?<a\\s*href=\"/forum1\\.php\\?config=hfr\\.inc&amp;cat=([0-9]+).*?\"\\s*class=\"cHeader\">(.*?)</a></th>)" +
			"|(<tr\\s*class=\"sujet\\s*ligne_booleen.*?(ligne_sticky)?\".*?" +
			"<td.*?class=\"sujetCase1.*?><img\\s*src=\".*?([A-Za-z0-9]+)\\.gif\".*?" +
			"<td.*?class=\"sujetCase3\".*?>(<span\\s*class=\"red\"\\s*title=\".*?\">\\[non lu\\]</span>\\s*)?.*?<a.*?class=\"cCatTopic\"\\s*title=\"Sujet n°([0-9]+)\">(.+?)</a></td>.*?" +
			"<td.*?class=\"sujetCase4\".*?(?:(?:<a.*?class=\"cCatTopic\">(.+?)</a>)|&nbsp;)</td>.*?" +
			"<td.*?class=\"sujetCase5\".*?(?:(?:<a\\s*href=\".*?#t([0-9]+)\"><img.*?src=\".*?([A-Za-z0-9]+)\\.gif\"\\s*title=\".*?\\(p\\.([0-9]+)\\)\".*?/></a>)|&nbsp;)</td>.*?" +
			"<td.*?class=\"sujetCase6.*?>(?:<a\\s*rel=\"nofollow\"\\s*href=\"/profilebdd.*?>)?(.+?)(?:</a>)?</td>.*?" +
			"<td.*?class=\"sujetCase7\".*?>(.+?)</td>.*?" +
			"<td.*?class=\"sujetCase9.*?>.*?class=\"Tableau\">" +
			"([0-9]+)-([0-9]+)-([0-9]+).*?([0-9]+):([0-9]+)<br /><b>(.+?)</b>.*?</td>.*?" +
			"</tr>))"
			, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        
        Log.d(HFR4droidApplication.TAG, "Matching topics of " + cat.getName());
		Matcher m = p.matcher(content);
		Category currentCat = cat;
		while (m.find())
		{
			/*System.out.println("--- NOUVEAU GROUPE ---");
			for(int i= 1; i<= m.groupCount(); ++i)
				System.out.println("groupe "+i+" :"+m.group(i));
			System.out.println("\n");*/

			if (m.group(1) != null)
			{
				// C'est une cat
				currentCat = new Category(Integer.parseInt(m.group(1)), m.group(2));
			}
			else
			{
	        	Matcher m2 = Pattern.compile("lock\\.gif").matcher(m.group(3));
	        	boolean isLocked = m2.find();
				TopicStatus status = isLocked ? TopicStatus.LOCKED : getStatusFromImgName(m.group(11) != null ? m.group(11) : m.group(5));
				int nbPages = m.group(9) != null ? Integer.parseInt(m.group(9)) : 1;
				int lastReadPage = status == TopicStatus.NEW_MP ? nbPages : (m.group(12) != null ? Integer.parseInt(m.group(12)) : -1);
				topics.add(new Topic(Integer.parseInt(m.group(7)),
									m.group(8),
									m.group(13),
									status,
									lastReadPage,
									m.group(10) != null ? Long.parseLong(m.group(10)) : -1,
									Integer.parseInt(m.group(14)),
									nbPages,
									new GregorianCalendar(Integer.parseInt(m.group(17)), // Year
											Integer.parseInt(m.group(16)) - 1, // Month
											Integer.parseInt(m.group(15)), // Day
											Integer.parseInt(m.group(18)), // Hour
											Integer.parseInt(m.group(19)), // Minute
											0  // Second
									).getTime(),
									m.group(20),
									m.group(4) != null,
									m.group(6) != null,
									currentCat
									)
				);
			}
		}
		Log.d(HFR4droidApplication.TAG, "Match OK, " + topics.size() + " topics retrieved");
		
		if (!cat.equals(Category.MPS_CAT)) checkNewMps(content);

		return topics;
	}

	private TopicStatus getStatusFromImgName(String imgName)
	{
		if (imgName == null)
		{
			return TopicStatus.NONE;
		}
		else if (imgName.equals("flag1"))
		{
			return TopicStatus.NEW_CYAN;
		}
		else if (imgName.equals("flag0"))
		{
			return TopicStatus.NEW_ROUGE;
		}
		else if (imgName.equals("favoris"))
		{
			return TopicStatus.NEW_FAVORI;
		}
		else if (imgName.equals("closed"))
		{
			return TopicStatus.NO_NEW_POST;
		}
		else if (imgName.equals("closedbp"))
		{
			return TopicStatus.NEW_MP;
		}
		else if (imgName.equals("closedp"))
		{
			return TopicStatus.NO_NEW_MP;
		}		
		else
		{
			return TopicStatus.NONE;	
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean setPostsAsRead(Topic topic, int pageNumber) throws DataRetrieverException
	{
		String url = POSTS_URL.replaceFirst("\\{\\$cat\\}", topic.getCategory().getRealId())
		.replaceFirst("\\{\\$topic\\}", String.valueOf(topic.getId()))
		.replaceFirst("\\{\\$page\\}", String.valueOf(pageNumber));
		
		Log.d(HFR4droidApplication.TAG, "Retrieving " + url);
		try
		{
			URI uri = new URI(url);
			HttpHead method = new HttpHead(uri);
			method.setHeader("User-Agent", "Mozilla /4.0 (compatible; MSIE 6.0; Windows CE; IEMobile 7.6) Vodafone/1.0/SFR_v1615/1.56.163.8.39");
			HttpContext httpContext = new BasicHttpContext();
			if (auth != null && auth.getCookies() != null && fakeCs != null)
			{
				httpContext.setAttribute(ClientContext.COOKIE_STORE, auth.getCookies());
			}
			
			HttpResponse response = httpClientHelper.getHttpClient().execute(method, httpContext);
			Log.d(HFR4droidApplication.TAG, "Status : " + response.getStatusLine().getStatusCode() + ", " + response.getStatusLine().getReasonPhrase());
			return response.getStatusLine().getStatusCode() == 200;
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_topics), e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public List<Post> getPosts(Topic topic, int pageNumber) throws DataRetrieverException
	{
		return getPosts(topic, pageNumber, false);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public List<Post> getPosts(Topic topic, int pageNumber, boolean useFakeAccount) throws DataRetrieverException
	{
		ArrayList<Post> posts = new ArrayList<Post>();
		String url = POSTS_URL.replaceFirst("\\{\\$cat\\}", topic.getCategory().getRealId())
		.replaceFirst("\\{\\$topic\\}", String.valueOf(topic.getId()))
		.replaceFirst("\\{\\$page\\}", String.valueOf(pageNumber));
		String content = null;
		try
		{
			content = getAsString(url, false, useFakeAccount);
			if (content == null) return null;
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_posts), e);
		}

        Pattern p = Pattern.compile(
        	"(<table\\s*cellspacing.*?class=\"([a-z]+)\">.*?" +
        	"<tr.*?class=\"message.*?" +
			"<a.*?href=\"#t([0-9]+)\".*?" +
			"<b.*?class=\"s2\">(?:<a.*?>)?(.*?)(?:</a>)?</b>.*?" +
			"(?:(?:<div\\s*class=\"avatar_center\".*?><img src=\"(.*?)\"\\s*alt=\".*?\"\\s*/></div>)|</td>).*?" +
			"<div.*?class=\"left\">Posté le ([0-9]+)-([0-9]+)-([0-9]+).*?([0-9]+):([0-9]+):([0-9]+).*?" +
			"<div.*?id=\"para[0-9]+\">(.*?)<div style=\"clear: both;\">\\s*</div></p>" +
			// "<div.*?id=\"para[0-9]+\">(.*?)<div class=\"clear\">\\s*</div></p>" + => nouvelle version
			"(?:<div\\s*class=\"edited\">)?(?:<a.*?>Message cité ([0-9]+) fois</a>)?(?:<br\\s*/>Message édité par .*? le ([0-9]+)-([0-9]+)-([0-9]+).*?([0-9]+):([0-9]+):([0-9]+)</div>)?.*?" +
			"</div></td></tr></table>)"
			, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Log.d(HFR4droidApplication.TAG,  topic.getName() != null ? "Matching posts of " + topic.getName() : "Matching posts");
        Matcher m = p.matcher(content);
        while (m.find())
        {
        	Matcher m2 = Pattern.compile("edit\\-in\\.gif").matcher(m.group(1));
        	boolean isMine = m2.find();
        	m2 = Pattern.compile("messageModo").matcher(m.group(1));
        	boolean isModo = m2.find();
        	boolean isDeleted = m.group(2).equals("messagetabledel");
        	String postContent = m.group(12);
        	posts.add(new Post(
        		Integer.parseInt(m.group(3)),
				postContent,
				m.group(4),
				m.group(5),
				new GregorianCalendar(Integer.parseInt(m.group(8)), // Year
						Integer.parseInt(m.group(7)) - 1, // Month
						Integer.parseInt(m.group(6)), // Day
						Integer.parseInt(m.group(9)), // Hour
						Integer.parseInt(m.group(10)), // Minute
						Integer.parseInt(m.group(11))  // Second
				).getTime(),
				m.group(14) != null ? new GregorianCalendar(Integer.parseInt(m.group(16)), // Year
						Integer.parseInt(m.group(15)) - 1, // Month
						Integer.parseInt(m.group(14)), // Day
						Integer.parseInt(m.group(17)), // Hour
						Integer.parseInt(m.group(18)), // Minute
						Integer.parseInt(m.group(19))  // Second
						).getTime() : null,
				m.group(13) != null ? Integer.parseInt(m.group(13)) : 0,
				isMine,
				isModo,
				isDeleted,
				topic
				)
			);
		}
        Log.d(HFR4droidApplication.TAG, "Match OK, " + posts.size() + " posts retrieved");

		String nbPages = getSingleElement("([0-9]+)</(?:a|b)></div><div\\s*class=\"pagepresuiv\"", content);
		if (nbPages != null) topic.setNbPages(Integer.parseInt(nbPages));

		if (!useFakeAccount) hashCheck = getSingleElement("<input\\s*type=\"hidden\"\\s*name=\"hash_check\"\\s*value=\"(.+?)\" />", content);
		
		String subCat = getSingleElement("<input\\s*type=\"hidden\"\\s*name=\"subcat\"\\s*value=\"([0-9]+)\"\\s*/>", content);
		if (subCat != null) topic.setSubCategory(new SubCategory(topic.getCategory(), Integer.parseInt(subCat)));
		
		// On vérifie si la notification par email est activé
		if (!useFakeAccount && topic.getStatus() != TopicStatus.LOCKED && !topic.getCategory().equals(Category.MPS_CAT))
		{
			String notif = getSingleElement("<input\\s*type=\"hidden\"\\s*name=\"emaill\"\\s*value=\"([0-9]+)\"\\s*/>", content);
			if (notif != null) topic.setEmailNotification(notif.equals("1"));
		}
		
		// Pour HFRUrlParser, récupération d'informations complémentaires
		if (topic.getName() == null)
		{
			//String topicTitle = getSingleElement("<input\\s*type=\"hidden\"\\s*name=\"sujet\"\\s*value=\"(.+?)\"\\s*/>", content);
			String topicTitle =  HFRDataRetriever.getSingleElement("<h3>(.*)</h3>", content);
			if (topicTitle != null) topic.setName(topicTitle);
			if (getSingleElement("(repondre\\.gif)", content) == null) topic.setStatus(TopicStatus.LOCKED);
		}

		if (!topic.getCategory().equals(Category.MPS_CAT)) checkNewMps(content);

		return posts;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public List<Post> searchPosts(Topic topic, String pseudo, String word, Post fromPost) throws DataRetrieverException
	{
		ArrayList<Post> posts = new ArrayList<Post>();
		String encodedPseudo = pseudo, encodedWord = word.replace("#", "");
		
		try
		{
			if (pseudo != null) encodedPseudo = URLEncoder.encode(pseudo, "UTF-8");
			if (word != null) encodedWord =  URLEncoder.encode(word.replace("#", ""), "UTF-8");
		}
		catch (UnsupportedEncodingException e1)
		{
			Log.w(HFR4droidApplication.TAG, e1);
		}
		
		String url = SEARCH_POSTS_URL.replaceFirst("\\{\\$cat\\}", topic.getCategory().getRealId())
		.replaceFirst("\\{\\$topic\\}", String.valueOf(topic.getId()))
		.replaceFirst("\\{\\$pseudo\\}", pseudo != null ? encodedPseudo : "")
		.replaceFirst("\\{\\$word\\}", word != null ? encodedWord : "")
		.replaceFirst("\\{\\$fp\\}", String.valueOf(fromPost != null ? fromPost.getId() : 0));
		String content = null;
		try
		{
			content = getAsString(url);
			if (content == null) return null;
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_posts), e);
		}

		Pattern p = Pattern.compile(
			"(<tr.*?class=\"message.*?" +
			"<a.*?href=\"#t([0-9]+)\".*?" +
			"<b.*?class=\"s2\">(?:<a.*?>)?(.*?)(?:</a>)?</b>.*?" +
			"(?:(?:<div\\s*class=\"avatar_center\".*?><img src=\"(.*?)\"\\s*alt=\".*?\"\\s*/></div>)|</td>).*?" +
			"<div.*?class=\"left\">Posté le ([0-9]+)-([0-9]+)-([0-9]+).*?([0-9]+):([0-9]+):([0-9]+).*?" +
			"</div></div><a\\s*href=\"(.*?)\".*?><b>Voir ce message dans le sujet non filtré</b></a>.*?" +
			"<div.*?id=\"para[0-9]+\">(.*?)<div style=\"clear: both;\">\\s*</div></p>" +
			"(?:<div\\s*class=\"edited\">)?(?:<a.*?>Message cité ([0-9]+) fois</a>)?(?:<br\\s*/>Message édité par .*? le ([0-9]+)-([0-9]+)-([0-9]+).*?([0-9]+):([0-9]+):([0-9]+)</div>)?.*?" +
			"</div></td></tr></table>)"
			, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

		Log.d(HFR4droidApplication.TAG,  topic.getName() != null ? "Matching posts of " + topic.getName() : "Matching posts");
		Matcher m = p.matcher(content);
		while (m.find())
		{
			Matcher m2 = Pattern.compile("edit\\-in\\.gif").matcher(m.group(1));
			boolean isMine = m2.find();
			m2 = Pattern.compile("messageModo").matcher(m.group(1));
			boolean isModo = m2.find();
			String postContent = m.group(12);
			PostFromSearch postFS = new PostFromSearch(
				Integer.parseInt(m.group(2)),
				postContent,
				m.group(3),
				m.group(4),
				new GregorianCalendar(Integer.parseInt(m.group(7)), // Year
						Integer.parseInt(m.group(6)) - 1, // Month
						Integer.parseInt(m.group(5)), // Day
						Integer.parseInt(m.group(8)), // Hour
						Integer.parseInt(m.group(9)), // Minute
						Integer.parseInt(m.group(10))  // Second
						).getTime(),
				m.group(14) != null ? new GregorianCalendar(Integer.parseInt(m.group(16)), // Year
						Integer.parseInt(m.group(15)) - 1, // Month
						Integer.parseInt(m.group(14)), // Day
						Integer.parseInt(m.group(17)), // Hour
						Integer.parseInt(m.group(18)), // Minute
						Integer.parseInt(m.group(19))  // Second
						).getTime() : null,
				m.group(13) != null ? Integer.parseInt(m.group(13)) : 0,
				isMine,
				isModo,
				false,
				topic
			);
			postFS.setCallbackUrl(BASE_URL + m.group(11).replace("&amp;", "&"));
			posts.add(postFS);
		}
		Log.d(HFR4droidApplication.TAG, "Match OK, " + posts.size() + " posts retrieved");

		String nbPages = getSingleElement("([0-9]+)</(?:a|b)></div><div\\s*class=\"pagepresuiv\"", content);
		if (nbPages != null) topic.setNbPages(Integer.parseInt(nbPages));

		hashCheck = getSingleElement("<input\\s*type=\"hidden\"\\s*name=\"hash_check\"\\s*value=\"(.+?)\" />", content);

		String subCat = getSingleElement("<input\\s*type=\"hidden\"\\s*name=\"subcat\"\\s*value=\"([0-9]+)\"\\s*/>", content);
		if (subCat != null) topic.setSubCategory(new SubCategory(topic.getCategory(), Integer.parseInt(subCat)));

		if (!topic.getCategory().equals(Category.MPS_CAT)) checkNewMps(content);
		
		return posts;
	}

	/**
	 * {@inheritDoc}
	 */
	public int countNewMps(Topic topic) throws DataRetrieverException
	{
		String content = null;
		try
		{
			content = getAsString(MPS_URL);
			if (content == null) return 0;
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_mps), e);
		}
		return innterCountNewMps(content, topic);
	}

	private int innterCountNewMps(String content, Topic topic) throws DataRetrieverException
	{
		int count = 0;
		Pattern p = Pattern.compile("<div\\s*class=\"left\"><div\\s*class=\"left\"><img\\s*src=\".*?newmp\\.gif\"\\s*alt=\"\"\\s*/>&nbsp;<a\\s*href=\"(.*?)\"\\s*class=\"red\">Vous avez ([0-9]+) nouveaux? messages? privés?</a>"
			, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

		Log.d(HFR4droidApplication.TAG, "Matching new mps");
		Matcher m = p.matcher(content);
		if (m.find())
		{
			count = Integer.parseInt(m.group(2));
			Matcher m2 = Pattern.compile("forum2.php.*?post=([0-9]+).*?page=([0-9]+)").matcher(m.group(1));
			if (m2.find())
			{
				topic.setId(Long.parseLong(m2.group(1)));
				topic.setStatus(TopicStatus.NEW_MP);
				topic.setNbPages(Integer.parseInt(m2.group(2)));
				topic.setCategory(Category.MPS_CAT);		
			}
		}
		Log.i(HFR4droidApplication.TAG, context.getString(R.string.new_mp_count, count));
		return count;
	}

	private void checkNewMps(String content) throws DataRetrieverException
	{
		if (context.isCheckMpsEnable())
		{
			Topic mp = new Topic(-1, null);
			int nbMps = innterCountNewMps(content, mp);
			Intent intent = new Intent(context, MpNotifyService.class);
			intent.putExtra("nbMps", nbMps);
			intent.putExtra("mp", mp);
			context.startService(intent);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public String getSmiliesByTag(String tag) throws DataRetrieverException
	{
		String encodedTag = tag;
		try
		{
			encodedTag = URLEncoder.encode(tag, "UTF-8");
		}
		catch (UnsupportedEncodingException e1)
		{
			Log.w(HFR4droidApplication.TAG, e1);
		}
		
		String url = SMILIES_URL.replaceFirst("\\{\\$tag\\}",  encodedTag);
		try
		{
			String content = getAsString(url);
			return content != null ? content : null;
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_smilies), e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public String getQuote(Post post) throws DataRetrieverException
	{
		String url = QUOTE_URL.replaceFirst("\\{\\$cat\\}", post.getTopic().getCategory().getRealId())
		.replaceFirst("\\{\\$topic\\}", String.valueOf(post.getTopic().getId()))
		.replaceFirst("\\{\\$post\\}", String.valueOf(post.getId()));
		return innerGetBBCode(url);
	}

	/**
	 * {@inheritDoc}
	 */
	public String getPostContent(Post post) throws DataRetrieverException
	{
		String url = EDIT_URL.replaceFirst("\\{\\$cat\\}", post.getTopic().getCategory().getRealId())
		.replaceFirst("\\{\\$topic\\}", String.valueOf(post.getTopic().getId()))
		.replaceFirst("\\{\\$post\\}", String.valueOf(post.getId()));
		return innerGetBBCode(url);
	}

	private String innerGetBBCode(String url) throws DataRetrieverException
	{
		StringBuilder result = new StringBuilder("");
		String content = null;
		try
		{
			content = getAsString(url, true, false);
			if (content == null) return null;
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_bbcode), e);
		}
		
		String BBCode = getSingleElement("<textarea.*?name=\"content_form\".*?>(.*?)</textarea>", content);
		if (BBCode != null)
		{
			for (String line : BBCode.split("\n"))
			{
				result.append(Html.fromHtml(line));
				result.append("\n");
			}
		}
		return result.toString();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getKeywords(String code) throws DataRetrieverException
	{
		String encodedCode = code;
		try
		{
			encodedCode = URLEncoder.encode(code, "UTF-8");
		}
		catch (UnsupportedEncodingException e1)
		{
			Log.w(HFR4droidApplication.TAG, e1);
		}

		String url = KEYWORDS_URL.replaceFirst("\\{\\$code\\}", encodedCode);
		String content = null;
		try
		{
			content = getAsString(url, true, false);
			if (content == null) return null;
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_keywords), e);
		}
		
		String keywords = getSingleElement("name=\"keywords0\"\\s*value=\"(.*?)\"\\s*onkeyup", content);
		return keywords;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Profile getProfile(String pseudo) throws DataRetrieverException
	{
		String encodedPseudo = pseudo;
		try
		{
			encodedPseudo = URLEncoder.encode(pseudo, "UTF-8");
		}
		catch (UnsupportedEncodingException e1)
		{
			Log.w(HFR4droidApplication.TAG, e1);
		}
		String url = PROFILE_URL.replaceFirst("\\{\\$pseudo\\}", encodedPseudo);
		
		Profile profile = null;
		String content = null;
		try
		{
			content = getAsString(url);
			if (content == null) return null;
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_profile), e);
		}

		Pattern p = Pattern.compile(
				"<td\\s*class=\"profilCase4\"\\s*rowspan=\"[0-9]\"\\s*style=\"text-align:center\">\\s*" +
						"(?:(?:<div\\s*class=\"avatar_center\"\\s*style=\"clear:both\"><img\\s*src=\"(.*?)\")|</td>).*?" +
						"<td\\s*class=\"profilCase2\">Date de naissance.*?</td>\\s*<td\\s*class=\"profilCase3\">(.*?)</td>.*?" +
						//"<td\\s*class=\"profilCase2\">Carte.*?</td>\\s*<td\\s*class=\"profilCase3\">(.*?)</td>.*?" +
						"<td\\s*class=\"profilCase2\">sexe.*?</td>\\s*<td\\s*class=\"profilCase3\">(.*?)</td>.*?" +
						"<td\\s*class=\"profilCase2\">ville.*?</td>\\s*<td\\s*class=\"profilCase3\">(.*?)</td>.*?" +
						"<td\\s*class=\"profilCase2\">Statut.*?</td>\\s*<td\\s*class=\"profilCase3\">(.*?)</td>.*?" +
						"<td\\s*class=\"profilCase2\">Nombre de messages postés.*?</td>\\s*<td\\s*class=\"profilCase3\">([0-9]+)</td>.*?" +
						"<td\\s*class=\"profilCase4\"\\s*rowspan=\"[0-9]\">(.*?)</td>.*?" +
						"<td\\s*class=\"profilCase2\">Date d'arrivée sur le forum.*?</td>\\s*<td\\s*class=\"profilCase3\">(.*?)</td>.*?" +
						"<td\\s*class=\"profilCase2\">Date du dernier message.*?</td>\\s*<td\\s*class=\"profilCase3\">(.*?)</td>"
				, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

		Log.d(HFR4droidApplication.TAG,  "Matching informations about " + pseudo);
		Matcher m = p.matcher(content);
		if (m.find())
		{
			SimpleDateFormat sdf1 = new SimpleDateFormat("dd/MM/yyyy");
			SimpleDateFormat sdf2 = new SimpleDateFormat("dd-MM-yyyy'&nbsp;à&nbsp;'HH:mm");
			/*
			String locationContent = m.group(3);
			Pattern p2 = Pattern.compile("<a\\s*class=\"cLink\"\\s*href=\"/hfr/carte/.*?\">(.*?)</a>");
			Matcher m2 = p2.matcher(locationContent);
			List<String> location = new ArrayList<String>();
			while (m2.find())
			{
				location.add(m2.group(1));
			}
			String[] locationArray = new String[location.size()];
			Collections.reverse(location);
			location.toArray(locationArray);*/
			
			String smileysContent = m.group(7);
			Pattern p3 = Pattern.compile("<img\\s*src=\"https://forum\\-images\\.hardware\\.fr/images/perso/((?:[0-9]/)?.*?)\"");
			Matcher m3 = p3.matcher(smileysContent);
			List<String> smileys = new ArrayList<String>();
			while (m3.find())
			{
				smileys.add(m3.group(1));
			}
			String[] smileysArray = new String[smileys.size()];
			smileys.toArray(smileysArray);

			profile = new Profile(
				pseudo,
				sdf1.parse(m.group(2).trim(), new ParsePosition(0)), // Date de naissance
				null,
				m.group(4).trim().equals("") ? null : m.group(5).trim(), // Ville
				Gender.fromString(m.group(3).trim()),
				Integer.parseInt(m.group(6)), // Nb messages postés
				ProfileType.fromString(m.group(5).trim()),
				sdf2.parse(m.group(9).trim(), new ParsePosition(0)), // Date du dernier message
				sdf1.parse(m.group(8).trim(), new ParsePosition(0)), // Date d'arrivée
				m.group(1),
				smileysArray);
		}

		return profile;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public List<AlertQualitay> getAlertsByTopic(Topic topic) throws DataRetrieverException
	{
		ArrayList<AlertQualitay> alerts = new ArrayList<AlertQualitay>();
		String url = AQ_BY_TOPIC_URL.replaceFirst("\\{\\$topic\\}", String.valueOf(topic.getId()));
		HttpClient<Document> client = new HttpClient<Document>(httpClientHelper)
		{		
			@Override
			protected Document transformStream(InputStream is) throws TransformStreamException
			{
				try
				{
					DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder = factory.newDocumentBuilder();
					return builder.parse(is);
				}
				catch (Exception e)
				{
					throw new TransformStreamException(e);
				}
			}
		};
		
		try
		{
			Document dom = client.doGet(url);
			if (dom == null) return null;
			Element root = dom.getDocumentElement();
			NodeList items = root.getElementsByTagName("alerte");
			for (int i = 0; i < items.getLength(); i++)
			{
				Node item = items.item(i);
				NamedNodeMap atts = item.getAttributes();
				SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
				
				String[] postsIdsStr = atts.getNamedItem("postsIds").getNodeValue().split(",");
				Long[] postsIds = new Long[postsIdsStr.length];
				for (int j = 0; j < postsIdsStr.length; j++) postsIds[j] = Long.parseLong(postsIdsStr[j]); 

				AlertQualitay alert = new AlertQualitay(
					Long.parseLong(atts.getNamedItem("id").getNodeValue()),
					atts.getNamedItem("nom").getNodeValue(),
					atts.getNamedItem("pseudoInitiateur").getNodeValue(),
					sdf.parse(atts.getNamedItem("date").getNodeValue()),
					postsIds);
				alerts.add(alert);	
			}
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(context.getString(R.string.error_dr_aqs), e);
		}

		return alerts;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getRealUrl(String url) throws DataRetrieverException
	{
		HttpParams params = new BasicHttpParams();
		try
		{
			URI uri = new URI(url);
			HttpHead method = new HttpHead(uri);
			method.setHeader("User-Agent", HFR4droidApplication.getUserAgent());
			
			// We disable redirecting
			HttpClientParams.setRedirecting(params, false);
			httpClientHelper.getHttpClient().setParams(params);
			HttpResponse response = httpClientHelper.getHttpClient().execute(method);
			if (response.getStatusLine().getStatusCode() == 301)
			{
				String rewrittenUrl = BASE_URL + response.getFirstHeader("Location").getValue();
				Log.d(HFR4droidApplication.TAG, "Rewritten url found : " + rewrittenUrl);
				return rewrittenUrl;
			}
			else
			{
				Log.e(HFR4droidApplication.TAG, "Unexpected " + response.getStatusLine().getStatusCode() + " code !");
				return null;
			}
		}
		catch (Exception e)
		{
			throw new DataRetrieverException(e.getMessage(), e);
		}
		finally
		{
			// We re-enable redirecting
			HttpClientParams.setRedirecting(params, true);
			httpClientHelper.getHttpClient().setParams(params);
		}
	}

	/**
	 * Effectue une requête HTTP GET et récupère un flux en retour
	 * @param url L'url concernée
	 * @return Un <code>InputStream</code> contenant le résultat
	 * @throws IOException Si un problème intervient durant la requête
	 * @throws URISyntaxException Si l'url est foireuse
	 * @throws ServerMaintenanceException Si le forum est en maintenance
	 */
	private String getAsString(String url) throws IOException, URISyntaxException, TransformStreamException, ServerMaintenanceException, NoSuchTopicException
	{
		return getAsString(url, false, false); 
	}

	/**
	 * Effectue une requête HTTP GET et récupère un flux en retour
	 * @param url L'url concernée
	 * @param cr Conserver les retours charriot
	 * @param useFakeAccount Utiliser ou pas un faux compte pour ne pas altérer les drapeaux
	 * @return Un <code>InputStream</code> contenant le résultat
	 * @throws IOException Si un problème intervient durant la requête
	 * @throws URISyntaxException Si l'url est foireuse
	 * @throws ServerMaintenanceException Si le forum est en maintenance
	 */
	private String getAsString(String url, final boolean cr, boolean useFakeAccount) throws IOException, URISyntaxException, TransformStreamException, ServerMaintenanceException, NoSuchTopicException
	{
		Log.d(HFR4droidApplication.TAG, "Retrieving " + url);

		HttpClient<String> client = new HttpClient<String>(httpClientHelper)
		{		
			@Override
			protected String transformStream(InputStream is) throws TransformStreamException
			{
				try
				{
					return streamToString(is, cr);
				}
				catch (IOException e)
				{
					throw new TransformStreamException(e);
				}
			}
		};

		String content = "";
		if (auth != null && auth.getCookies() != null && fakeCs != null)
		{
			Log.d(HFR4droidApplication.TAG, "CPU usage : " + context.getCPUUsage() + "%");
			content = client.doGet(url, useFakeAccount ? fakeCs : auth.getCookies());
		}
		else
		{
			content =  client.doGet(url);
		}
		
		if (content != null)
		{
			if  (content.contains(MAINTENANCE)) throw new ServerMaintenanceException(context.getString(R.string.server_maintenance));
			if  (content.contains(TOPIC_DELETED)) throw new NoSuchTopicException(context.getString(R.string.no_such_topic));
			Log.d(HFR4droidApplication.TAG, "GET OK for " + url);
		}
		return content;
	}

	/**
	 * Convertit un <code>InputStream</code> en <code>String</code>
	 * @param is Le flux d'entrée
	 * @param cr Conserver les retours charriot
	 * @return La chaine ainsi obtenu
	 * @throws IOException Si un probléme d'entrée/sortie intervient
	 */
	public static String streamToString(InputStream is, boolean cr) throws IOException
	{
		if (is != null)
		{
			StringBuilder sb = new StringBuilder();
			String line;
			try
			{
				BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				while ((line = reader.readLine()) != null)
				{
					sb.append(line);
					if (cr) sb.append("\n");
				}
			}
			finally
			{
				is.close();
			}
			return sb.toString();
		}
		else
		{        
			return "";
		}
	}
	
	/**
	 * Renvoie le premier match ou le second si le premier est null, du premier groupe trouvé dans une chaine donnée.
	 * @param pattern La regexp é appliquer
	 * @param content Le contenu é analyser
	 * @return La chaine trouvée, null sinon
	 */
	public static String getSingleElement(String pattern, String content)
	{
		Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(content);
		return m.find() ? (m.group(1) != null ? m.group(1) : m.group(2)) : null;
	}
	
	private void serializeCats() throws DataRetrieverException
	{
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;

		try
		{
		    File cacheDir = context.getCacheDir();
		    if (!cacheDir.exists()) cacheDir.mkdirs();

	        fos = new FileOutputStream(new File(context.getCacheDir(), CATS_CACHE_FILE_NAME));
			oos = new ObjectOutputStream(fos);
			oos.writeObject(cats);
		}
		catch (Exception e) // FileNotFoundException, IOException
		{
			throw new DataRetrieverException(context.getString(R.string.error_serializing_cats), e);
		}
		finally
		{
			if (oos != null) try { oos.close(); } catch (IOException e) {} 
		}
		Log.d(HFR4droidApplication.TAG, "Serializing " + cats.keySet().size() + " categories");
	}
	
	@SuppressWarnings("unchecked")
	private boolean deserializeCats() throws DataRetrieverException
	{
		FileInputStream fis = null;
		ObjectInputStream ois = null;
		try
		{
			fis = new FileInputStream(new File(context.getCacheDir(), CATS_CACHE_FILE_NAME));
			ois = new ObjectInputStream(fis);
			cats = (Map<Category, List<SubCategory>>) ois.readObject();
		}
		catch (FileNotFoundException e)
		{
			Log.d(HFR4droidApplication.TAG, "No cache yet");
			return false;
		}
		catch (ClassNotFoundException e) // Pour gérer le changement de nom des packages
		{
			Log.w(HFR4droidApplication.TAG, "Wrong classname for the cookies, cancelling the auto-login");
			return false;
		}
		catch (Exception e) // ClassNotFoundException, IOException
		{
			throw new DataRetrieverException(context.getString(R.string.error_deserializing_cats), e);
		}
		finally
		{
			if (ois != null) try { ois.close(); } catch (IOException e) {} 
		}
		Log.d(HFR4droidApplication.TAG, "Deserializing " + cats.keySet().size() + " categories");
		return true;
	}
	
	private void clearCache()
	{
		File f = new File(context.getCacheDir(), CATS_CACHE_FILE_NAME);
		f.delete();
		Log.d(HFR4droidApplication.TAG, "Destroying categories");
	}
}
