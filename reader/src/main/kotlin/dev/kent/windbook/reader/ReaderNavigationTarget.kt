package dev.kent.windbook.reader

import kotlin.math.abs
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

enum class NavigationResult { COMPLETED, UNCHANGED, FAILED }

internal fun semanticReadingLocator(coarse: Locator,visible: Locator?): Locator {
    if(visible==null || visible.href!=coarse.href) return coarse
    return visible.copy(title=coarse.title,locations=visible.locations.copy(
        position=coarse.locations.position,progression=coarse.locations.progression,totalProgression=coarse.locations.totalProgression))
}
internal fun navigationTargetMatches(target: Locator,current: Locator,elementVisible: Boolean?=null): Boolean {
    if(target.href!=current.href) return false
    if(target.locations.otherLocations["cssSelector"] is String || target.locations.fragments.isNotEmpty() || !target.text.highlight.isNullOrEmpty()) return elementVisible==true
    target.locations.position?.let {return it==current.locations.position}
    target.locations.progression?.let {goal ->return current.locations.progression?.let {abs(it-goal)<.00001}==true}
    return true
}

/** Validates the quote and its context before translating its normalized offset to a DOM Range. */
private fun targetScript(target: Locator,scroll: Boolean): String? {
    val selector=target.locations.otherLocations["cssSelector"] as? String
    val fragment=target.locations.fragments.firstOrNull()?.let(android.net.Uri::decode)
    val quote=target.text.highlight.orEmpty()
    if(selector==null && fragment==null && quote.isEmpty()) return null
    require((selector?.length ?: 0)<=4096 && (fragment?.length ?: 0)<=4096 && quote.length<=4096)
    val root=selector?.let {"document.querySelector(${JSONObject.quote(it)})"}
        ?: fragment?.let {"document.getElementById(${JSONObject.quote(it)})"} ?: "document.body"
    val offset=(target.locations.otherLocations["windbookTextOffset"] as? Number)?.toInt() ?: -1
    return """
        (function(){try{
          const root=$root;if(!root)return false;
          let range=root;
          const quote=${JSONObject.quote(quote)},before=${JSONObject.quote(target.text.before.orEmpty())},after=${JSONObject.quote(target.text.after.orEmpty())};
          if(quote){
            const nodes=[],walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let raw='',node;
            while(node=walker.nextNode()){
              if(node.parentElement.closest('script,style,noscript'))continue;
              nodes.push({node:node,start:raw.length});raw+=node.data;if(raw.length>2097152)return false;
            }
            const normalized=raw.replace(/\s+/g,' ').trim();
            const valid=at=>at>=0&&normalized.slice(at,at+quote.length)===quote&&
              (!before||normalized.slice(Math.max(0,at-before.length),at)===before)&&
              (!after||normalized.slice(at+quote.length,at+quote.length+after.length)===after);
            let at=$offset;
            if(!valid(at)){
              at=-1;let next=normalized.indexOf(quote);
              while(next>=0){if(valid(next)){if(at>=0)return false;at=next;}next=normalized.indexOf(quote,next+1);}
            }
            if(at<0)return false;
            let count=0,started=false,start=-1,end=-1;
            for(let i=0;i<raw.length;){
              let j=i+1;const space=/\s/.test(raw[i]);if(space)while(j<raw.length&&/\s/.test(raw[j]))j++;
              if(!space||started){
                if(count===at)start=i;
                if(count===at+quote.length-1){end=j;break;}
                count++;started=true;
              }i=j;
            }
            if(start<0||end<0)return false;
            const first=nodes.find(n=>start>=n.start&&start<n.start+n.node.length);
            const last=nodes.find(n=>end>n.start&&end<=n.start+n.node.length);
            if(!first||!last)return false;
            range=document.createRange();range.setStart(first.node,start-first.start);range.setEnd(last.node,end-last.start);
          }
          const rects=Array.from(range.getClientRects());
          const visible=()=>rects.some(r=>r.width>0&&r.height>0&&r.left<innerWidth&&r.right>0&&r.top<innerHeight&&r.bottom>0);
          if(${scroll}&&!visible()){
            const rect=rects.find(r=>r.width>0&&r.height>0);if(!rect)return false;
            const style=getComputedStyle(document.documentElement),element=document.scrollingElement;
            const horizontal=element.scrollWidth>innerWidth+1;
            const reverse=style.direction==='rtl'||style.writingMode.startsWith('vertical-rl');
            const distance=horizontal?(reverse?Math.abs(scrollX)+innerWidth-rect.right:scrollX+rect.left):scrollY+rect.top;
            readium.scrollToPosition(Math.max(0,distance/(horizontal?element.scrollWidth:element.scrollHeight)));
          }
          return visible();
        }catch(e){return false;}})()
    """.trimIndent()
}
internal fun navigationVisibilityScript(target: Locator): String? = targetScript(target,false)
internal fun navigationScrollScript(target: Locator): String? = targetScript(target,true)

/** Capture only a bounded quote at the visible caret, rather than returning a whole long element. */
internal val visibleReadingLocatorScript="""
    (function(){try{
      const loc=readium.findFirstVisibleLocator();if(!loc)return null;
      const root=document.querySelector(loc.locations.cssSelector);if(!root)return null;
      const rects=Array.from(root.getClientRects());let caret=null;
      for(const r of rects){
        const left=Math.max(1,r.left),right=Math.min(innerWidth-1,r.right),top=Math.max(1,r.top),bottom=Math.min(innerHeight-1,r.bottom);
        for(let y=top+4;y<bottom&&!caret;y+=16)for(let x=left+4;x<right&&!caret;x+=24){
          const c=document.caretRangeFromPoint(x,y);if(c&&root.contains(c.startContainer)&&c.startContainer.nodeType===Node.TEXT_NODE)caret=c;
        }if(caret)break;
      }
      if(!caret)return null;
      const raw=root.textContent;if(raw.length>2097152)return null;
      const prefix=document.createRange();prefix.selectNodeContents(root);prefix.setEnd(caret.startContainer,caret.startOffset);
      const text=raw.replace(/\s+/g,' ').trim();
      let offset=prefix.toString().replace(/\s+/g,' ').trimStart().length;offset=Math.min(offset,Math.max(0,text.length-1));
      loc.locations.windbookTextOffset=offset;
      loc.text={before:text.slice(Math.max(0,offset-48),offset),highlight:text.slice(offset,offset+96),after:text.slice(offset+96,offset+176)};
      return loc;
    }catch(e){return null;}})()
""".trimIndent()
