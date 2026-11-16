package dev.kent.windbook.reader

import dev.kent.windbook.core.model.EpubTextAnchor
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

internal fun epubSelectionAnchor(locator: Locator, captured: String?): EpubTextAnchor? = try {
    val data=JSONObject(captured ?: return null)
    val quote=data.getString("quote")
    val before=data.getString("before"); val after=data.getString("after")
    val anchored=locator.copy(locations=locator.locations.copy(otherLocations=locator.locations.otherLocations+
        mapOf("cssSelector" to "body","windbookTextOffset" to data.getInt("offset"))),
        text=Locator.Text(before=before,highlight=quote,after=after))
    EpubTextAnchor(href=anchored.href.toString(),locatorJson=anchored.toJsonString(),quote=quote,before=before,after=after).takeIf {it.isValid()}
} catch(_: Exception) {null}

/** Capture the exact occurrence, including identical sentences, in a bounded normalized DOM stream. */
internal val epubSelectionScript="""
(function(){try{
 const s=window.getSelection();if(!s||s.rangeCount!==1||s.isCollapsed)return null;
 const r=s.getRangeAt(0),nodes=[],w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
 let raw='',n;while(n=w.nextNode()){
  if(n.parentElement.closest('script,style,noscript'))continue;
  nodes.push({n:n,start:raw.length});raw+=n.data;if(raw.length>2097152)return null;
 }
 const a=nodes.find(x=>x.n===r.startContainer),b=nodes.find(x=>x.n===r.endContainer);if(!a||!b)return null;
 let start=a.start+r.startOffset,end=b.start+r.endOffset;
 while(start<end&&/\s/.test(raw[start]))start++;
 while(end>start&&/\s/.test(raw[end-1]))end--;
 const text=raw.replace(/\s+/g,' ').trim(),quote=raw.slice(start,end).replace(/\s+/g,' ');
 if(!quote||quote.length>4096)return null;
 const at=raw.slice(0,start).replace(/\s+/g,' ').trimStart().length;
 if(text.slice(at,at+quote.length)!==quote)return null;
 return {quote:quote,before:text.slice(Math.max(0,at-160),at),after:text.slice(at+quote.length,at+quote.length+160),offset:at};
}catch(e){return null;}})()
""".trimIndent()
