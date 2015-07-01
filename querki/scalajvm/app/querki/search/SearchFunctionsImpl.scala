package querki.search

import scala.concurrent.Future
import querki.globals._
import querki.session.AutowireApiImpl
import querki.session.AutowireParams

class SearchFunctionsImpl(info:AutowireParams)(implicit e:Ecology) extends AutowireApiImpl(info, e) with SearchFunctions {
  
  import SearchFunctions._

  lazy val ClientApi = interface[querki.api.ClientApi]
  lazy val Search = interface[querki.search.Search]
  
  def doRoute(req:Request):Future[String] = route[SearchFunctions](this)(req)
  
  def search(q:String):Option[SearchResults] = {
    implicit val s = state
    val rawResultsOpt = Search.search(q)
    rawResultsOpt.map { rawResults =>
      val transformed = rawResults.results.map { result =>
        SearchResult(
          ClientApi.thingInfo(result.thing, rc),
          result.prop.displayName,
          result.score,
          result.text,
          result.positions
        )
      }
      SearchResults(q, transformed)
    }
  }
}