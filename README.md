# APIHarvester
An application for harvesting XML metadata records from Trove, DigitalNZ, and similar "Web APIs".
APIHarvester can also be used to extract portions of an XML file stored on a local file system, simply by specifying a `file:` URI in the request. This makes it usable e.g. for checking quality of bulk metadata records.

APIHarvester is [available as an executable Java archive (jar) file](https://github.com/Conal-Tuohy/APIHarvester/releases/latest). 

Running it without any parameters produces the following explanatory output:

```
APIHarvester is a tool to harvest XML records from a web API. APIHarvester will:

 • download a response from a given URL, if necessary retrying in the event of failure
 • split the response into multiple records which match an XPath expression
 • save each record under a filename specified using another XPath expression
 • continue harvesting from additional URLs, extracted from the response using another XPath expression

APIHarvester is controlled using XPath 1 expressions; see https://www.w3.org/TR/xpath/ for details.

Usage:

java -jar apiharvester.jar [parameter list]

Parameters are specified as [key=value]. Values containing spaces, ampersands, etc should be enclosed in quotes.
XML namespace prefixes can be bound to namespace URIs using 'xmlns:' parameters.

Parameters:

 • xmlns:foo
      Binds the 'foo' namespace prefix to a namespace URI, for use in the XPath expressions.
 • directory
      Location of output files. If not specified, the current directory is used.
 • url
      Initial URL to harvest from - required.
 • records-xpath
      XPath identifying the individual records within a response. If not specified, the entire response is saved as a single record.
 • id-xpath
      XPath of unique id for each record, evaluated within the context of each record - required.
 • discard-xpath
      XPath of elements or text which should be discarded, evaluated within the context of each record.
 • resume-when-xpath
      XPath determining whether to resume from a harvest page or not - default = "true()"
 • resumption-xpath
      XPath of URL or URLs for subsequent pages of data - if not specified only the initial URL will be harvested)
 • url-suffix
      Specifies a common suffix for URLs; useful for specifying an 'API key' for some APIs.
 • retries
      Specifies a number of times to retry in the event of any error; default is 3
 • delay
      Specifies a number of seconds to wait between requests; default is 0.
 • indent
      Specifies whether to indent the XML or not. Valid values are "yes" or "no". If unspecified, the value is "no".

Example:

java -jar apiharvester.jar retries=4 xmlns:foo="http://example.com/ns/foo" url="http://example.com/api?foo=bar" records-xpath="/foo:response/foo:result" id-xpath="concat('record-', @id)" discard-xpath="*[not(normalize-space())]" resumption-xpath="concat('/api?foo=bar&page=', /foo:response/@page-number + 1)" url-suffix="&api_key=asdkfjasd" indent=yes delay=10
```

See the [Wiki](https://github.com/Conal-Tuohy/APIHarvester/wiki) for real examples
