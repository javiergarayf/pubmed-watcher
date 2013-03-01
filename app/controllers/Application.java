package controllers;

import play.*;
import play.data.validation.Required;
import play.libs.WS;
import play.libs.XPath;
import play.libs.WS.HttpResponse;
import play.mvc.*;

import java.util.*;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import models.*;

@With(Security.class)
public class Application extends Controller {

	static User connected() {
		String email = session.get("email");
		if(email != null) {
			return User.find("byEmail", email).first();
		}
		return null; 
	}

	public static void index() {
		User user = connected();
		String updated = session.get("updated");
		if(updated == null || updated.equals("false")){
			user.updateRelatedArticles();
			session.put("updated", "true");
		}
		renderArgs.put("email", user.email);
		List<KeyArticle> keyArticles = user.keyArticles;
		List<RelatedArticle> relatedArticles = RelatedArticle.find("user=? order by standardizedSimilarity desc", user).fetch(10);

		render(relatedArticles, keyArticles);
	}

	public static void addKeyArticle() {
		render();
	}

	public static void removeKeyArticle(long id) {
		connected().removeKeyArticle(id);
		session.put("updated", "false");
		index();
	}
	
	public static void markAsRead(long id) {
		connected().markAsRead(id);
		index();
	}
	
	public static void unMarkAsRead(int pmid) {
		connected().unMarkAsRead(pmid);
		session.put("updated", "false");
		index();
	}
	
	public static void read(){
		List<Integer> readArticlePmids = connected().readArticlePmids;
		render(readArticlePmids);
	}

	public static void addnewKeyArticle(int pmid) {

		if(connected().keyArticles.size() > 4){
			//TODO handle problem in connection too and configure errors
			index();
		}

		//http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&id=11850928,11482001
		HttpResponse res = WS.url("http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?" +
				"db=pubmed&id=" + pmid).get();
		int status = res.getStatus();
		//TODO If bad status then go back to home page + error message
		System.out.println("status key article: " + status);
		Document xml = res.getXml();
		//Iterates over the XML results and get pmids and scores out

		if(XPath.selectNodes("//DocSum", xml).size() == 0){
			//TODO handle problem in connection too and configure errors
			addKeyArticle();
		}

		//Create and save a new key article
		for(Node article: XPath.selectNodes("//DocSum", xml)) {
			KeyArticle newKeyArticle = new KeyArticle(connected(), pmid);
			newKeyArticle.date = XPath.selectText("Item[@Name='PubDate']", article);
			newKeyArticle.title = XPath.selectText("Item[@Name='Title']", article);
			newKeyArticle.journal = XPath.selectText("Item[@Name='Source']", article);
			List<Node> authors = XPath.selectNodes("//DocSum/Item[@Name='AuthorList']", article);
			newKeyArticle.authors = XPath.selectText("Item[1]", authors);
			connected().keyArticles.add(newKeyArticle);
			newKeyArticle.save();
			connected().save();
			System.out.println("new article added to the existing ones");
		} 

		session.put("updated", "false");
		index();
	}
}