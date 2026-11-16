package dev.kent.windbook.core.data.learning

import org.json.JSONObject

data class LearningWord(val term:String,val meaning:String)
data class LearningAnswer(val translation:String,val grammar:String,val idioms:String,val context:String,val words:List<LearningWord>) {
    companion object {
        fun parse(raw:String):LearningAnswer {
            require(raw.length<=32768)
            val json=raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val root=JSONObject(json)
            fun field(name:String)=root.getString(name).also {require(it.isNotBlank() && it.length<=12000)}
            val words=root.getJSONArray("words");require(words.length()<=20)
            return LearningAnswer(field("translation"),field("grammar"),field("idioms"),field("context"),(0 until words.length()).map {
                val w=words.getJSONObject(it);LearningWord(w.getString("term"),w.getString("meaning")).also {v ->require(v.term.isNotBlank() && v.term.length<=256 && v.meaning.isNotBlank() && v.meaning.length<=2048)}
            })
        }
    }
}
