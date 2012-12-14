package models

import play.api._

import models.system._
import models.system.OIDs._
import models.system.SystemSpace
import models.system.SystemSpace._

/**
 * Enumeration of what sort of Thing this is. Note that this is an intentionally
 * exclusive set. That's mostly to make it reasonably easy to reason about stuff:
 * if something is a Type, that means it isn't ordinary.
 */
object Kind {
  type Kind = Int
  
  val Thing = 0
  val Type = 1
  val Property = 2
  val Space = 3
  val Collection = 4
  val Attachment = 5
}

object Thing {
  type PropMap = Map[OID, PropValue[_]]
  type PropFetcher = () => PropMap
  
  // A couple of convenience methods for the hard-coded Things in System:
  def toProps(pairs:(OID,PropValue[_])*):PropFetcher = () => {
    (Map.empty[OID, PropValue[_]] /: pairs) { (m:Map[OID, PropValue[_]], pair:(OID, PropValue[_])) =>
      m + (pair._1 -> pair._2)
    }
  }
  
  def emptyProps = Map.empty[OID, PropValue[_]]
  
  // NOTE: don't try to make this more concise -- it causes chicken-and-egg problems in system
  // initialization:
  def setName(str:String):(OID,PropValue[_]) = 
    (NameOID -> PropValue(OneColl(ElemValue(str))))

  // TODO: this escape/unescape is certainly too simplistic to cope with recursive types.
  // Come back to this sometime before we make the type system more robust.
  def escape(str:String) = {
    str.replace("\\", "\\\\").replace(";", "\\;").replace(":", "\\:").replace("}", "\\}").replace("{", "\\{")
  }
  def unescape(str:String) = {
    str.replace("\\{", "{").replace("\\}", "}").replace("\\:", ":").replace("\\;", ";").replace("\\\\", "\\")
  }
  
  def serializeProps(props:PropMap, space:SpaceState) = {
    val serializedProps = props.map { pair =>
      val (ptr, v) = pair
      val prop = space.prop(ptr)
      val oid = prop.id
      oid.toString + 
        ":" + 
        Thing.escape(prop.serialize(prop.castVal(v)))
    }
    
    serializedProps.mkString("{", ";", "}")
  }    
  
  def deserializeProps(str:String, space:SpaceState):PropMap = {
    // Strip the surrounding {} pair:
    val stripExt = str.slice(1, str.length() - 1)
    // Note that we have to split on semicolons that are *not* preceded by backslashes. This is
    // a little tricky to express in regex -- the weird bit is saying "things that aren't backslashes,
    // non-capturing".
    val propStrs = stripExt.split("""(?<=[^\\]);""")
    val propPairs = propStrs.filter(_.trim.length() > 0).map { propStr =>
      val (idStr, valStrAndColon) = propStr.splitAt(propStr.indexOf(':'))
      val valStr = unescape(valStrAndColon.drop(1))
      val id = OID(idStr)
      val prop = space.prop(id)
      val v = prop.deserialize(valStr)
      (id, v)
    }
    toProps(propPairs:_*)()
  }
}

import Thing._

/**
 * The root concept of the entire world. Thing is the Querki equivalent of Object,
 * the basis of the entire type system.
 * 
 * TODO: note that we thread the whole thing with OIDs, to make it easier to build the
 * whole potentially-immutable stack. Down the line, we might add a second pass that
 * re-threads these things with hard references, to make them faster to process. This
 * should do to start, though.
 */
abstract class Thing(
    val id:OID, 
    val spaceId:OID, 
    val model:OID, 
    val kind:Kind.Kind,
    val propFetcher: PropFetcher)
{
  lazy val props:PropMap = propFetcher()
  
  def displayName:String = {
    val localName = localProp(DisplayNameProp) orElse localProp(NameProp)
    if (localName.isEmpty)
      id.toString
    else {
      localName.get.render.raw
    }
  }
  
  def canonicalName:Option[String] = {
    NameProp.firstOpt(props)
  }
  
  def toThingId:ThingId = {
    val nameOpt = canonicalName
    nameOpt map AsName getOrElse AsOID(id)
  }

  def getModel(implicit state:SpaceState):Thing = { state.anything(model) }
  
  /**
   * The Property as defined on *this* specific Thing.
   */
  def localProp(pid:OID)(implicit state:SpaceState):Option[PropAndVal[_,_]] = {
    val ptr = state.prop(pid)
    props.get(pid).map(v => ptr.pair(v))
  }
  def localProp[VT, CT](prop:Property[VT, _, CT]):Option[PropAndVal[VT,CT]] = {
    prop.fromOpt(this.props) map prop.pair
  }
  
  /**
   * The key method for fetching a Property Value from a Thing. This walks the tree
   * as necessary.
   * 
   * Note that this walks up the tree recursively. It eventually ends with UrThing,
   * which does things a little differently.
   */
  def getProp(propId:OID)(implicit state:SpaceState):PropAndVal[_,_] = {
    // TODO: we're doing redundant lookups of the property. Rationalize this stack of calls.
    val prop = state.prop(propId)
    if (prop.first(NotInheritedProp))
      localOrDefault(propId)
    else
      localProp(propId).getOrElse(getModel.getProp(propId))
  }
  
  def localPropVal[VT, CT](prop:Property[VT, _, CT]):Option[PropValue[CT]] = {
    prop.fromOpt(props)
  }
  
  def localOrDefault(propId:OID)(implicit state:SpaceState):PropAndVal[_,_] = {
    val prop = state.prop(propId)
    localProp(propId).getOrElse(prop.defaultPair)
  }
    
  /**
   * If you have the actual Property object you're looking for, this returns its value
   * on this object in a typesafe way.
   */
  def getPropVal[VT, CT](prop:Property[VT, _, CT])(implicit state:SpaceState):PropValue[CT] = {
    val local = localPropVal(prop)
    if (local.isDefined)
      local.get
    else if (prop.first(NotInheritedProp))
      prop.default
    else
      getModel.getPropVal(prop)
  }

  /**
   * Return the first value in the collection for this Type. This is a convenient, type-safe way to
   * get at a property value for ExactlyOne properties.
   * 
   * IMPORTANT: this will throw an Exception if you try to call it on an Optional that is empty!
   * In general, while it is syntactically legal to call this on an Optional type, it's usually
   * inappropriate.
   */
  def first[VT, CT](prop:Property[VT, _, CT])(implicit state:SpaceState):VT = {
    prop.first(getPropVal(prop))
  }
  
  def localFirst[VT, CT](prop:Property[VT, _, CT])(implicit state:SpaceState):Option[VT] = {
    localPropVal(prop) map (prop.first(_))
  }
  
  /**
   * Returns true iff this Thing or any ancestor has the specified property defined on it.
   * Note that this ignores defaults.
   */
  def hasProp(propId:OID)(implicit state:SpaceState):Boolean = {
    props.contains(propId) || getModel.hasProp(propId)
  }
  
  /**
   * Convenience method -- returns either the value of the specified property or None.
   */
  def getPropOpt(propId:OID)(implicit state:SpaceState):Option[PropAndVal[_,_]] = {
    if (hasProp(propId))
      Some(getProp(propId))
    else
      None
  }
  
  def localProps(implicit state:SpaceState):Set[Property[_,_,_]] = {
    props.keys.map(state.prop(_)).toSet    
  }
  
  /**
   * Lists all of the Properties defined on this Thing and its ancestors.
   */
  def allProps(implicit state:SpaceState):Set[Property[_,_,_]] = {
    localProps ++ getModel.allProps
  }
  
  def renderProps(implicit state:SpaceState):Wikitext = {
    val listMap = props.map { entry =>
      val prop = state.prop(entry._1)
      val pv = prop.pair(entry._2)
      "<dt>" + prop.displayName + "</dt><dd>" + pv.render.raw + "</dd>"
    }
    Wikitext(listMap.mkString("<dl>", "", "</dl>"))    
  }
  
  /**
   * Every Thing can be rendered -- this returns a Wikitext string that will then be
   * displayed in-page.
   * 
   * TODO: allow this to be redefined with a QL Property if desired.
   */
  def render(implicit state:SpaceState):Wikitext = {
    val opt = getPropOpt(DisplayTextProp)
    opt.map(pv => pv.render).getOrElse(renderProps)
  }
  

  def serializeProps(implicit state:SpaceState) = Thing.serializeProps(props, state)
  
  def export(implicit state:SpaceState):String = {
    "{" +
    "id:" + id.toString + ";" +
    "model:" + model.id.toString + ";" +
    "kind:" + kind.toString + ";" +
    "props:" + serializeProps +
    "}"
  }
}

/**
 * A ThingState represents the value of a Thing as of a particular time.
 * It is immutable -- you change the Thing by going to its Space and telling it
 * to make the change.
 * 
 * Note that Models are basically just ordinary Things.
 */
case class ThingState(i:OID, s:OID, m:OID, pf: PropFetcher, k:Kind.Kind = Kind.Thing) 
  extends Thing(i, s, m, k, pf) {}