# APIHarvester
An application for harvesting metadata records from Trove, DigitalNZ, and similar APIs

APIHarvester is [available as an executable Java archive (jar) file](https://github.com/Conal-Tuohy/APIHarvester/releases/tag/1.0). 

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

Parameters are specified as [key=value]. Values containing spaces should be enclosed in quotes.

directory (location of output files - default is current directory)
url (initial URL to harvest from - required)
records-xpath (xpath identifying the individual records within a response - default is to save entire response as a single record)
id-xpath (xpath of unique id for each record, evaluated within the context of each record - required)
resumption-xpath (xpath of URL or URLs for subsequent pages of data - default is to harvest only from the initial URL)
url-suffix (specifies a common suffix for URLs; useful for specifying an 'API key')
retries (specifies a number of times to retry, in the event of any error; default is 3)

Example:

java -jar apiharvester.jar retries=4 url="http://example.com/api?foo=bar" records-xpath="/response/result" id-xpath="concat('record-', @id)" resumption-xpath="concat('/api?foo=bar&page=', /response/@page-number + 1)" url-suffix="&api_key=asdkfjasd"

```

See the [Wiki](https://github.com/Conal-Tuohy/APIHarvester/wiki) for real examples
