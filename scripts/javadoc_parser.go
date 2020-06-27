package main

import (
	"fmt"
	"strings"

	"golang.org/x/net/html"
)

func populateClassInfoFromHtml(ci *classInfo, doc *html.Node) error {
	visitMemberRow := func(row *html.Node) {
		addIdentifier(ci, row)
	}

	doForEachMemberRow(doc, visitMemberRow)
	return nil
}

func addIdentifier(ci *classInfo, row *html.Node) {
	var (
		identifier string
		static     bool
	)
	for n := row.FirstChild; n != nil; n = n.NextSibling {
		if !isElement(n, "td") {
			continue
		}

		var isFirstCol, isLastCol bool
		className, _ := getAttributeValue(n, "class")
		if className == "colFirst" {
			isFirstCol = true
		}

		if className == "colLast" {
			isLastCol = true
		}

		if isLastCol == isFirstCol {
			// We cannot handle it
			fmt.Printf("unknown cell: %v\n", n)
		}

		if isLastCol {
			identifier = getIdentifier(n)
			continue
		}

		static = isStatic(n)
	}

	if identifier != "" {
		if static {
			ci.staticIdentifiers = append(ci.staticIdentifiers, identifier)
			return
		}

		ci.visibleIdentifiers = append(ci.visibleIdentifiers, identifier)
	}
}

// getIdentifier extracts the identifier from a "class=colLast" td.
//
// td is supposed to contain at most one link, and the identifier is the text
// inside
func getIdentifier(td *html.Node) string {
	var explore func(*html.Node) string
	explore = func(n *html.Node) string {
		if isElement(n, "a") {
			if n.FirstChild != n.LastChild {
				panic("link with more than one child")
			}

			if n.FirstChild.Type != html.TextNode {
				panic("link child is not text")
			}

			return n.FirstChild.Data
		}

		var combined string
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			combined += explore(c)
		}

		return combined
	}

	return explore(td)
}

func isStatic(parent *html.Node) bool {
	innerText := getInnerText(parent)
	return strings.HasPrefix(innerText, "static")
}

func getInnerText(parent *html.Node) string {
	var explore func(*html.Node) string
	explore = func(n *html.Node) string {
		if n.Type == html.TextNode {
			return n.Data
		}

		var combined string
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			combined += explore(c)
		}

		return combined
	}

	return explore(parent)
}

func doForEachMemberRow(n *html.Node, callback func(*html.Node)) {
	if isMemberSummary(n) {
		doForEachBodyRow(n, callback)
		return
	}

	for c := n.FirstChild; c != nil; c = c.NextSibling {
		doForEachMemberRow(c, callback)
	}
}

func isMemberSummary(n *html.Node) bool {
	if !isElement(n, "table") {
		return false
	}

	class, found := getAttributeValue(n, "class")
	if !found {
		return false
	}

	return class == "memberSummary"
}

func doForEachBodyRow(n *html.Node, callback func(*html.Node)) {
	if isElement(n, "tbody") {
		doForEachRow(n, callback)
		return
	}

	for c := n.FirstChild; c != nil; c = c.NextSibling {
		doForEachBodyRow(c, callback)
	}
}

func doForEachRow(n *html.Node, callback func(*html.Node)) {
	if isElement(n, "tr") {
		callback(n)
		return
	}

	for c := n.FirstChild; c != nil; c = c.NextSibling {
		doForEachRow(c, callback)
	}
}

func findAllClassPrefixes(doc *html.Node) []string {
	var prefixes = make([]string, 0)
	// The only "a" elements are the ones contained in the big list of all
	// available classes
	storePrefix := func(a *html.Node) {
		if prefix, found := getAttributeValue(a, "href"); found {
			prefixes = append(prefixes, prefix)
		}
	}

	visitAnchorTags(doc, storePrefix)
	return prefixes
}

func visitAnchorTags(n *html.Node, callback func(*html.Node)) {
	if isElement(n, "a") {
		callback(n)
	}

	for c := n.FirstChild; c != nil; c = c.NextSibling {
		visitAnchorTags(c, callback)
	}
}

func getAttributeValue(n *html.Node, attribute string) (value string, found bool) {
	for _, attr := range n.Attr {
		if attr.Key == attribute {
			return attr.Val, true
		}
	}

	return "", false
}

func isElement(n *html.Node, tag string) bool {
	return n.Type == html.ElementNode && n.Data == tag
}
