package querki.uservalues

import models.{OID, Property, PType, Wikitext}

import querki.util.QLog
import querki.values.{QLContext, SpaceState}

case class DiscreteSummary[UVT](content:Map[UVT,Int])

/**
 * This Summarizer is intended for any Type with a limited number of discrete values. The Summary is one
 * integer for each value, giving the number of times it has been specified.
 * 
 * The userType is the PType we are actually summarizing values of. We mostly need it for serialization.
 */
class DiscreteSummarizer[UVT](userType:PType[UVT]) extends Summarizer[UVT,DiscreteSummary[UVT]] {
  def addToSummary(tid:OID, prop:Property[DiscreteSummary[UVT],_], previous:Option[UVT], current:Option[UVT])(implicit state:SpaceState):DiscreteSummary[UVT] = {
    state.anything(tid) match {
      case Some(thing) => {
	    thing.getPropOpt(prop) match {
	      case Some(oldSummary) => {
	        // TBD: the "first" below is suspicious. Can it ever be wrong?
	        val oldMap = oldSummary.first.content
	        // First, decrement the previous key, if there was one...
	        val mapWithoutPrevious = previous match {
	          case Some(prevKey) if (oldMap.contains(prevKey)) => {
	            val prevVal = (oldMap(prevKey) - 1)
	            if (prevVal == 0)
	              oldMap - prevKey
	            else
	              oldMap + (prevKey -> prevVal) 
	          }
	          case _ => oldMap
	        }
	        // ... then increment the new key, if there is one:
	        val mapWithCurrent = current match {
	          case Some(curKey) => {
	            if (mapWithoutPrevious.contains(curKey))
	              // We already have records with that key:
	              mapWithoutPrevious + (curKey -> (mapWithoutPrevious(curKey) + 1))
	            else
	              // First time someone's given this value:
	              mapWithoutPrevious + (curKey -> 1)
	          }
	          // We've simply removed the previous value, without giving a new one:
	          case _ => mapWithoutPrevious
	        }
	        DiscreteSummary(mapWithCurrent)
	      }
	      
	      case None => {
	        current match {
	          // First value for this Thing:
	          case Some(curVal) => DiscreteSummary(Map(curVal -> 1))
	          // TODO: should this be a warning? Kind of strange to get a "change" that contains no value, if
	          // there wasn't one before. What's the change?
	          case None => DiscreteSummary(Map())
	        }
	      }
	    }        
      }
      
      case None => {
        QLog.error(s"Got addToSummary for unknown Thing $tid")
        DiscreteSummary(Map())
      }
    }
  }
  
  def doDeserialize(ser:String)(implicit state:SpaceState):DiscreteSummary[UVT] = {
    // TODO: this should unescape "," and ":", so we can cope with arbitrary Strings:
    val pairStrs = ser.split(",")
    // Filter out the empty-string case:
    val pairs = if (pairStrs.length > 0 && pairStrs(0).length() > 0) pairStrs.map { pairStr =>
        val parts = pairStr.split(":")
        if (parts.length != 2)
          throw new Exception(s"DiscreteSummarizer.doDeserialize got a bad pair: $pairStr")
      
        (userType.doDeserialize(parts(0)), java.lang.Integer.parseInt(parts(1)))
    } else
      Array.empty[(UVT,Int)]
    
    DiscreteSummary(Map(pairs:_*))
  }
  
  def doSerialize(summary:DiscreteSummary[UVT])(implicit state:SpaceState):String = {
    val pairStrs = summary.content.map { pair =>
      val (key, num) = pair
      userType.doSerialize(key) + ":" + num.toString
    }
    pairStrs.mkString(",")
  }
  
  def doWikify(context:QLContext)(v:DiscreteSummary[UVT], displayOpt:Option[Wikitext] = None):Wikitext = {
    // TODO: this should become a fancy histogram. But for now, keep it simple:
    // TODO: to do this properly, including zero values, we need to know the range of
    // userType, so that we can iterate over it!
    val valueStrs = v.content.map { pair =>
      val (key, num) = pair
      "* " + userType.doWikify(context)(key) + ": " + num.toString
    }
    Wikitext(valueStrs.mkString("\n", "\n", "\n"))
  }
  
  def doDefault(implicit state:SpaceState):DiscreteSummary[UVT] = DiscreteSummary(Map())
}