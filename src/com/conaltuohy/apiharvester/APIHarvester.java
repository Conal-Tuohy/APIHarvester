package com.conaltuohy.apiharvester;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Stack;
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
			System.err.println("Harvest failed:");
			System.err.println(e.getMessage());
			System.out.println();
			printUsage();
		}
	}
	
	private static void printUsage() {
		System.out.println("APIHarvester is a tool to harvest XML records from a web API. APIHarvester will:");
		System.out.println();
		System.out.println(" • download a response from a given URL, if necessary retrying in the event of failure");
		System.out.println(" • split the response into multiple records which match an XPath expression");
		System.out.println(" • save each record under a filename specified using another XPath expression");
		System.out.println(" • continue harvesting from additional URLs, extracted from the response using another XPath expression");
		System.out.println();
		System.out.println("APIHarvester is controlled using XPath 1 expressions; see https://www.w3.org/TR/xpath/ for details.");
		System.out.println();
		System.out.println("Usage:");
		System.out.println();
		System.out.println("java -jar apiharvester.jar [parameter list]");
		System.out.println();
		System.out.println("Parameters are specified as [key=value]. Values containing spaces should be enclosed in quotes.");
		System.out.println();
		System.out.println("directory (location of output files - default is current directory)");
		System.out.println("url (initial URL to harvest from - required)");
		System.out.println("records-xpath (xpath identifying the individual records within a response - default is to save entire response as a single record)");
		System.out.println("id-xpath (xpath of unique id for each record, evaluated within the context of each record - required)");
		System.out.println("resumption-xpath (xpath of URL or URLs for subsequent pages of data - default is to harvest only from the initial URL)");
		System.out.println("url-suffix (specifies a common suffix for URLs; useful for specifying an 'API key')");
		System.out.println("retries (specifies a number of times to retry, in the event of any error; default is 3)");
		System.out.println();
		System.out.println("Example:");
		System.out.println();
		System.out.println("java -jar apiharvester.jar retries=4 url=\"http://example.com/api?foo=bar\" records-xpath=\"/response/result\" id-xpath=\"concat('record-', @id)\" resumption-xpath=\"concat('/api?foo=bar&page=', /response/@page-number + 1)\" url-suffix=\"&api_key=asdkfjasd\"");
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
	}
	
	private void checkArgument(String name, String message) {
		if (!arguments.containsKey(name)) {
			throw new IllegalArgumentException(message);
		}
	}

	private Stack<URL> urls = new Stack<URL>();
	
	private void run() throws Exception {
		if (arguments.isEmpty()) {
			printUsage();
		} else {
			// check required parameters are present
			checkArgument("url", "The 'url' argument is required");
			checkArgument("id-xpath", "The 'id-xpath' argument is required");
			
			// create output directory if needed
			String outputDirectory = arguments.get("directory");
			if (outputDirectory != null) {
				new File(outputDirectory).mkdirs();
			}
			
			URL url = new URL(arguments.get("url") + getArgument("url-suffix", ""));
			urls.push(url);
			do {
				url = urls.pop();
				harvest(url);
			} while (! urls.isEmpty());
		}
	}
	
	private int getRetries() {
		return Integer.valueOf(getArgument("retries", "3"));
	}
	
	private void harvest(URL url) throws Exception {
		// harvests from a URL, queues up additional URLs to harvest from
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
		// remember any resumption URLs
		NodeList resumptionURLs = getResumptionURLs(doc);
		if (resumptionURLs != null) {
			for (int i = resumptionURLs.getLength() - 1; i >= 0; i--) {
				String relativeResumptionURL = resumptionURLs.item(i).getNodeValue();
				// resolve the resumption URL relative to the current URL
				URL resumptionURL = new URL(url, relativeResumptionURL + getArgument("url-suffix", ""));
				urls.push(resumptionURL);
			}
		}
	}
	
	private NodeList getResumptionURLs(Document doc) throws XPathExpressionException {
		NodeList resumptionURLs = null;
		String resumptionXPath = arguments.get("resumption-xpath");
		if (resumptionXPath != null) {
			XPath xpath = XPathFactory.newInstance().newXPath();
			resumptionURLs = (NodeList) xpath.evaluate(
				resumptionXPath,
				doc,
				XPathConstants.NODESET
			);
		}
		return resumptionURLs;
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
	
	private Document load(URL url) throws Exception {
		return load(url, getRetries());
	}
	
	private Document load(URL url, int retriesRemaining) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		try {
			return factory.newDocumentBuilder().parse(url.openStream());
		} catch (Exception e) {
			System.out.println("Error reading XML. " + String.valueOf(retriesRemaining) + " retries remaining.");
			if (retriesRemaining > 0) {
				Thread.sleep(5000); // wait 5s before retrying
				return load(url, retriesRemaining - 1);
			} else {
				throw e;
			}
		}
	}		
}
