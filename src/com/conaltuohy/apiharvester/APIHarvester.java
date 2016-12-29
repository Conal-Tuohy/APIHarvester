package com.conaltuohy.apiharvester;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.net.URL;
import java.net.URLEncoder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource; 
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;


public class APIHarvester {
	public static void main(String[] args) {
		try {
			APIHarvester h = new APIHarvester(args);
			h.run();
		} catch (Exception e) {
			System.err.println("Harvest failed");
			System.err.println(e.getMessage());
		}
	}
	
	private HashMap<String, String> arguments;
	private APIHarvester(String[] args) {
		// parse the parameters, specified as key=value pairs, into a map
		arguments = new HashMap(args.length);
		for (String argument: args) {
			int delimiterPosition = argument.indexOf("=");
			if (delimiterPosition == -1) {
				throw new IllegalArgumentException("The '=' symbol is missing: " + argument);
			}
			String key = argument.substring(0, delimiterPosition);
			if (key.length() == 0) {
				throw new IllegalArgumentException("The argument name is missing: " + argument);
			}
			String value = argument.substring(delimiterPosition + 1);
			if (value.length() == 0) {
				throw new IllegalArgumentException("The argument value is missing: " + argument);
			}
			arguments.put(key, value);
		}
		//System.out.println(arguments);
		
		// check required parameters are present
		checkArgument("url", "The 'url' argument is required");
		checkArgument("id-xpath", "The 'id-xpath' argument is required");
	}
	
	private void checkArgument(String name, String message) {
		if (!arguments.containsKey(name)) {
			throw new IllegalArgumentException(message);
		}
	}
	
	private void run() throws Exception {
		String url = arguments.get("url");
		do {
			url = harvest(url);
		} while (url != null);
	}
	
	private int getRetries() {
		return Integer.valueOf(getArgument("retries", "3"));
	}
	
	private String harvest(String url) throws Exception {
		// harvests from a url, returns the url to continue harvesting, or null if harvest complete
		System.out.println("harvesting from " + url + " ...");
		// load the XML document
		Document doc = load(url);
		// parse the XML into separate records
		NodeList records = getRecords(doc);
		for (int i = records.getLength() - 1; i >= 0; i--) {
			//System.out.println("Saving record " + Integer.valueOf(i) + " ...");
			Node record = records.item(i);
			save(record);
		}
		// TODO handle resumption
		return null;
	}
	
	private void save(Node record)
		throws 
			FileNotFoundException, 
			TransformerConfigurationException, 
			TransformerException, 
			XPathExpressionException,
			UnsupportedEncodingException
	{
		// serialize record to file
		File file = new File(
			arguments.get("directory"),
			getFilename(record)
		);
		OutputStream out = new FileOutputStream(file);
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer t = tf.newTransformer();
		DOMSource source = new DOMSource(record);
		StreamResult result = new StreamResult(out);
		t.transform(source, result);
	}
	
	private String getFilename(Node record) 
		throws 
			XPathExpressionException,
			UnsupportedEncodingException
	{
		XPath xpath = XPathFactory.newInstance().newXPath();
		String id = (String) xpath.evaluate(
			arguments.get("id-xpath"),
			record,
			XPathConstants.STRING
		);
		return URLEncoder.encode(id, "UTF-8") + ".xml";
	}
	
	private String getArgument(String name, String defaultValue) {
		if (arguments.containsKey(name)) {
			return arguments.get(name);
		} else {
			return defaultValue;
		}
	}
	
	private String getRecordsXPath() {
		return getArgument("records-xpath", "/*");
	}
	
	private NodeList getRecords(Document doc) throws XPathExpressionException {
		XPath xpath = XPathFactory.newInstance().newXPath();
		return (NodeList) xpath.evaluate(
			getRecordsXPath(),
			doc,
			XPathConstants.NODESET
		);
	}
	
	private Document load(String url) throws Exception {
		return load(url, getRetries());
	}
	
	private Document load(String url, int retriesRemaining) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		try {
			return factory.newDocumentBuilder().parse(new URL(url).openStream());
		} catch (Exception e) {
			System.out.println("Error reading XML. " + String.valueOf(retriesRemaining) + " retries remaining.");
			if (retriesRemaining > 0) {
				return load(url, retriesRemaining - 1);
			} else {
				throw e;
			}
		}
	}		
}
