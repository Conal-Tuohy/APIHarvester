package com.conaltuohy.apiharvester;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;
import java.net.URL;
import java.net.URLEncoder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.OutputKeys;
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
		System.out.println("Parameters are specified as [key=value]. Values containing spaces, ampersands, etc should be enclosed in quotes.");
		System.out.println("XML namespace prefixes can be bound to namespace URIs using 'xmlns:' parameters.");
		System.out.println();
		System.out.println("Parameters:");
		System.out.println();
		System.out.println(" • xmlns:foo");
		System.out.println("      Binds the 'foo' namespace prefix to a namespace URI, for use in the XPath expressions.");
		System.out.println(" • directory");
		System.out.println("      Location of output files. If not specified, the current directory is used.");
		System.out.println(" • url");
		System.out.println("      Initial URL to harvest from - required.");
		System.out.println(" • records-xpath");
		System.out.println("      XPath identifying the individual records within a response. If not specified, the entire response is saved as a single record.");
		System.out.println(" • id-xpath");
		System.out.println("      XPath of unique id for each record, evaluated within the context of each record - required.");
		System.out.println(" • discard-xpath");
		System.out.println("      XPath of elements or text which should be discarded, evaluated within the context of each record.");
		System.out.println(" • resume-when-xpath");
		System.out.println("      XPath determining whether to resume from a harvest page or not - default = \"true()\"");
		System.out.println(" • resumption-xpath");
		System.out.println("      XPath of URL or URLs for subsequent pages of data - if not specified only the initial URL will be harvested)");
		System.out.println(" • url-suffix");
		System.out.println("      Specifies a common suffix for URLs; useful for specifying an 'API key' for some APIs.");
		System.out.println(" • retries");
		System.out.println("      Specifies a number of times to retry in the event of any error; default is 3");
		System.out.println(" • delay");
		System.out.println("      Specifies a number of seconds to wait between requests; default is 0.");
		System.out.println(" • indent");
		System.out.println("      Specifies whether to indent the XML or not. Valid values are \"yes\" or \"no\". If unspecified, the value is \"no\".");
		System.out.println();
		System.out.println("Example:");
		System.out.println();
		System.out.println("java -jar apiharvester.jar retries=4 xmlns:foo=\"http://example.com/ns/foo\" url=\"http://example.com/api?foo=bar\" records-xpath=\"/foo:response/foo:result\" id-xpath=\"concat('record-', @id)\" discard-xpath=\"*[not(normalize-space())]\" resumption-xpath=\"concat('/api?foo=bar&page=', /foo:response/@page-number + 1)\" url-suffix=\"&api_key=asdkfjasd\" indent=yes delay=10");
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
				throw new IllegalArgumentException("The parameter name is missing: " + argument);
			}
			String value = argument.substring(delimiterPosition + 1);
			if (value.length() == 0) {
				throw new IllegalArgumentException("The parameter value is missing: " + argument);
			}
			arguments.put(key, value);
		}
		if (!arguments.isEmpty()) {
			System.out.println("Harvest will run with the following parameters:");
			System.out.println();
			for (Map.Entry<String, String> argument : arguments.entrySet()) {
				System.out.println(argument.getKey() + "=" + argument.getValue());
			}
			System.out.println();
		}
	}
	
	private void checkArgument(String name, String message) {
		if (!arguments.containsKey(name)) {
			throw new IllegalArgumentException(message);
		}
	}

	private Stack<URL> urls = new Stack<URL>();
	private int harvested = 0;
	XPathExpression recordsXPath = null;
	XPathExpression idXPath = null;
	XPathExpression resumptionXPath = null;
	XPathExpression resumeWhenXPath = null;
	XPathExpression discardXPath = null;
	private Transformer transformer = null;
	
	private void run() throws Exception {
		if (arguments.isEmpty()) {
			printUsage();
		} else {
			// check required parameters are present
			checkArgument("url", "The 'url' argument is required");
			checkArgument("id-xpath", "The 'id-xpath' argument is required");
			
			// compile XPath expressions
			XMLNamespaceContext namespaces = new XMLNamespaceContext();
			for (String key : arguments.keySet()) {
				if (key.startsWith("xmlns:")) {
					// argument specifies an XML namespace binding
					String prefix = key.substring(6); // after "xmlns:"
					String namespaceURI = arguments.get(key);
					namespaces.bind(prefix, namespaceURI);
				}
			}
			XPath xpath = XPathFactory.newInstance().newXPath();
			xpath.setNamespaceContext(namespaces);
			recordsXPath = xpath.compile(getRecordsXPath());
			idXPath = xpath.compile(arguments.get("id-xpath"));
			if (arguments.containsKey("resumption-xpath")) {
				resumptionXPath = xpath.compile(arguments.get("resumption-xpath"));
			}
			resumeWhenXPath = xpath.compile(getArgument("resume-when-xpath", "true()"));
			if (arguments.containsKey("discard-xpath")) {
				discardXPath = xpath.compile(arguments.get("discard-xpath"));
			}
			
			// prepare transformer for serializing records
			TransformerFactory tf = TransformerFactory.newInstance();
			transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, getIndent());			
			
			// create output directory if needed
			String outputDirectory = arguments.get("directory");
			if (outputDirectory != null) {
				new File(outputDirectory).mkdirs();
			}
			
			URL url = new URL(arguments.get("url") + getArgument("url-suffix", ""));
			harvest(url);
			while (! urls.isEmpty()) {
				// one or more "resumption" URLs remain to be harvested
				Thread.sleep(getDelay());
				url = urls.pop();
				harvest(url);
			};
			
			System.out.println();
			System.out.println(
				String.valueOf(harvested) +
				((harvested == 1) ? " record was harvested." : " records were harvested.")
			);
		}
	}
	
	private int getRetries() {
		return Integer.valueOf(getArgument("retries", "3"));
	}
	
	private long getDelay() {
		return Long.valueOf(getArgument("delay", "0")) * 1000;
	}
	
	private void harvest(URL url) throws Exception {
		// harvests from a URL, queues up additional URLs to harvest from
		System.out.println("harvesting from " + url + " ...");
		// load the XML document
		Document doc = load(url);
		// parse the XML into separate records
		NodeList records = getRecords(doc);
		for (int i = records.getLength() - 1; i >= 0; i--) {
			Node record = records.item(i);
			// discard any unwanted parts of the record
			if (discardXPath != null) {
				NodeList discards =  (NodeList) discardXPath.evaluate(record, XPathConstants.NODESET);
				for (int j = discards.getLength() - 1; j >= 0; j--) {
					Node discard = discards.item(j);
					switch (discard.getNodeType()) {
						case Node.ATTRIBUTE_NODE:
							System.out.println("Cannot discard attribute nodes");
							break;
						case Node.DOCUMENT_NODE:
							System.out.println("Cannot discard document node");
							break;
						default:
							if (discard.isSameNode(record)) {
								System.out.println("Cannot discard entire record");
							} else {
								Node parent = discard.getParentNode();
								if (parent != null) {
									parent.removeChild(discard);
								}
							}
					}
				}
			}
			//System.out.println("Saving record " + Integer.valueOf(i) + " ...");
			save(record);
		}
		// remember any resumption URLs
		Stack<String> resumptionURLs = getResumptionURLs(doc);
		//NodeList resumptionURLs = getResumptionURLs(doc);
		for (String relativeResumptionURL : resumptionURLs) {
			// resolve the resumption URL relative to the current URL
			URL resumptionURL = new URL(url, relativeResumptionURL + getArgument("url-suffix", ""));
			urls.push(resumptionURL);
		}
	}
	
	private Stack<String> getResumptionURLs(Document doc) throws XPathExpressionException {
		Stack<String> resumptionURLs = new Stack<String>();
		if (resumptionXPath != null) {
			try {
				// first check whether we should resume
				Boolean resume = (Boolean) resumeWhenXPath.evaluate(
					doc,
					XPathConstants.BOOLEAN
				);
				if (resume) {
					// generate a list of resumption URLs
					NodeList resumptionURLNodes = null;
					resumptionURLNodes = (NodeList) resumptionXPath.evaluate(
						doc,
						XPathConstants.NODESET
					);
					for (int i = resumptionURLNodes.getLength() - 1; i >= 0; i--) {
						String relativeResumptionURL = resumptionURLNodes.item(i).getTextContent();
						resumptionURLs.push(relativeResumptionURL);
					}
				}
			} catch (Exception e) {
				// accept a single resumption URL as a String
				String relativeResumptionURL = (String) resumptionXPath.evaluate(
					doc,
					XPathConstants.STRING
				);
				if (!"".equals(relativeResumptionURL)) {
					resumptionURLs.push(relativeResumptionURL);
				}
			}
		}
		return resumptionURLs;
	}
	
	private String getIndent() {
			return getArgument("indent", "no");
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
		DOMSource source = new DOMSource(record);
		OutputStream out = new FileOutputStream(file);
		StreamResult result = new StreamResult(out);
		transformer.transform(source, result);
		harvested++;
	}
	
	private String getFilename(Node record) 
		throws 
			XPathExpressionException,
			UnsupportedEncodingException
	{
		XPath xpath = XPathFactory.newInstance().newXPath();
		String id = (String) idXPath.evaluate(
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
		return (NodeList) recordsXPath.evaluate(
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
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
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
	
	private class XMLNamespaceContext implements NamespaceContext {
		private HashMap<String, String> bindings = new HashMap<String, String>();
		
		private void bind(String prefix, String namespaceURI) {
			bindings.put(prefix, namespaceURI);
		}
		
		// NamespaceContext implementation
		public String getNamespaceURI(String prefix) {
			return bindings.get(prefix);
		}
		
		public String getPrefix(String namespaceURI) throws IllegalArgumentException {
			if (namespaceURI == null) {
				throw new IllegalArgumentException();
			};
			for (Map.Entry<String, String> binding : bindings.entrySet()) {
				if (namespaceURI.equals(binding.getValue())) {
					return binding.getKey();
				}
			}
			return null;
		}

		public Iterator<String> getPrefixes(String namespaceURI) throws IllegalArgumentException {
			if (namespaceURI == null) {
				throw new IllegalArgumentException();
			};
			Vector<String> prefixes = new Vector<String>();
			for (Map.Entry<String, String> binding : bindings.entrySet()) {
				if (namespaceURI.equals(binding.getValue())) {
					prefixes.add(binding.getKey());
				}
			}
			return prefixes.iterator();
		}
	}
}
