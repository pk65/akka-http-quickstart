package priv.home.tools

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import priv.home.tools.UserRegistry._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir._
import com.typesafe.scalalogging.StrictLogging
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserRoutes(userRegistry: ActorRef[UserRegistry.Command])(implicit val system: ActorSystem[_]) extends StrictLogging {

  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  import io.circe.generic.auto._

  private val limitParameter = query[Option[Int]]("limit").description("Maximum number of users to retrieve")

  val baseEndpoint = endpoint.errorOut(
    oneOf[ErrorInfo](
      oneOfVariantFromMatchType(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
      oneOfVariantFromMatchType(StatusCode.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
      oneOfVariantFromMatchType(StatusCode.Unauthorized, jsonBody[AuthenticationError].description("unauthenticated")),
      oneOfVariantFromMatchType(StatusCode.BadRequest, jsonBody[MissingParameter].description("missing parameter")),
      oneOfVariantFromMatchType(StatusCode.InternalServerError, jsonBody[InternalServerError].description("internal server error")),
    )
  )

  val userEndpointAll: PublicEndpoint[Limit, ErrorInfo, ResponseBody, Any] = baseEndpoint.get
    .in("users")
    .in(limitParameter.example(Option(10)))
    .out(jsonBody[ResponseBody])

  val userEndpointInfo: PublicEndpoint[UsersQuery, ErrorInfo, ResponseBody, Any] = baseEndpoint.get
    .in(("users" / path[String]("name").map(Option(_))(_.get)).and(limitParameter.example(Option(1))).mapTo[UsersQuery])
    .out(jsonBody[ResponseBody])

  val userEndpointSecureCreate: Endpoint[AuthenticationToken, User, ErrorInfo, ResponseBody, Any] = baseEndpoint.post
    .in("users")
    .securityIn(auth.bearer[String]().mapTo[AuthenticationToken])
    .in(jsonBody[User]
      .description("The user to add in a secure way")
      .example(User("John Dow", 99, "XY")))
    .out(
      oneOf[ResponseBody](
        oneOfVariantFromMatchType(StatusCode.Created, jsonBody[ResponseSuccessWithPayload].description("Created successfully")),
        oneOfVariantFromMatchType(StatusCode.Ok, jsonBody[ResponseUpdateExisting].description("Created with failure")),
      )
    )

  val userEndpointDelete: Endpoint[AuthenticationToken, UsersQuery, ErrorInfo, ResponseBody, Any] = baseEndpoint.delete
    .in(("users" / path[String]("name").map(Option(_))(_.get)).and(limitParameter.example(Option.empty[Int])).mapTo[UsersQuery])
    .securityIn(auth.bearer[String]().mapTo[AuthenticationToken])
    .out(
      oneOf[ResponseBody](
        oneOfVariantFromMatchType(StatusCode.Ok, jsonBody[ResponseSuccessWithPayload].description("Deleted successfully")),
      )
    )

  def swaggerUIServerEndpoints: List[ServerEndpoint[Any, Future]] = {
    import sttp.tapir.swagger.bundle.SwaggerInterpreter

    // interpreting the endpoint descriptions as yaml openapi documentation
    // exposing the docs using SwaggerUI endpoints, interpreted as an akka-http route
    SwaggerInterpreter().fromEndpoints(List(userEndpointAll, userEndpointInfo, userEndpointSecureCreate, userEndpointDelete), "Akka Http Quickstart", "1.0")
  }

  def getUsers(query: UsersQuery): Future[Either[ErrorInfo, ResponseBody]] = {
    userRegistry.ask(GetUsers(query, _)).flatMap(users => Future {
      Right(UsersResult("Ok", users.users))
    })
  }

  def userDeleteLogic(principal: UserPrincipal, query: UsersQuery): Future[Either[ErrorInfo, ResponseBody]] = {
    if (List("berries", "smurf").contains(principal.token.value)) {
      userRegistry.ask(DeleteUser(query, _)).flatMap(ret => Future {
        if (ret.users.isEmpty) {
          Left(NotFound(s"""User ${query.name.get} couldn't be found!"""))
        } else {
          Right(ResponseSuccessWithPayload(ret.users.last))
        }
      })
    } else {
      Future {
        Left(Unauthorized(query.name.get))
      }
    }
  }

  def userAddLogic(principal: UserPrincipal, user: User): Future[Either[ErrorInfo, ResponseBody]] =
    if (List("berries", "smurf").contains(principal.token.value)) {
      userRegistry.ask(CreateUser(user, _)).flatMap(ret => Future {
        if (ret.users.isEmpty) {
          Left(InternalServerError(s"""User ${user.name} couldn't be saved!"""))
        } else {
          Right(ResponseSuccessWithPayload(user))
        }
      })
    } else {
      Future {
        Left(Unauthorized(user.name))
      }
    }

  def authenticate(token: AuthenticationToken): Future[Either[ErrorInfo, UserPrincipal]] =
    Future[Either[ErrorInfo, UserPrincipal]] {
      if (token.value == "berries") Right(UserPrincipal("Papa Smurf", token))
      else if (token.value == "smurf") Right(UserPrincipal("Gargamel", token))
      else Left(AuthenticationError(1001))
    }

  private val userEndpoints =
    userEndpointInfo.serverLogic(query => getUsers(query)) ::
      userEndpointAll.serverLogic(limit => getUsers(UsersQuery(Option.empty, limit))) ::
//      userEndpointCreate.serverLogic((userAddLogic _).tupled) ::
      userEndpointSecureCreate
        .serverSecurityLogic(authenticate)
        .serverLogic(p => u => userAddLogic(p, u)) ::
      userEndpointDelete
        .serverSecurityLogic(authenticate)
        .serverLogic(p => u => userDeleteLogic(p, u)) :: Nil

  val customServerOptions: AkkaHttpServerOptions = AkkaHttpServerOptions
    .customiseInterceptors
    .decodeFailureHandler(
      DefaultDecodeFailureHandler.default)
    .options

  val userRoutes: Route =
    AkkaHttpServerInterpreter(customServerOptions).toRoute(userEndpoints ++ swaggerUIServerEndpoints)


}
