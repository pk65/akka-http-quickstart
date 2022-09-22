package priv.home.tools

//#user-registry-actor
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.immutable

//#user-case-classes
final case class User(name: String, age: Int, countryOfResidence: String)
final case class Users(users: immutable.Seq[User])
//#user-case-classes

object UserRegistry {
  type Limit = Option[Int]
  type AuthToken = String

  case class UsersQuery(name: Option[String], limit: Limit)

  sealed trait ErrorInfo

  case class NotFound(message: String) extends ErrorInfo

  case class Unauthorized(realm: String) extends ErrorInfo

  case class MissingParameter(message: String) extends ErrorInfo

  case class InternalServerError(message: String) extends ErrorInfo

  case class AuthenticationError(code: Int) extends ErrorInfo

  sealed trait Command
  final case class GetUsers(query: UsersQuery,replyTo: ActorRef[Users]) extends Command
  final case class CreateUser(user: User, replyTo: ActorRef[Users]) extends Command
  final case class DeleteUser(query: UsersQuery,replyTo: ActorRef[Users]) extends Command

  sealed trait ResponseBody
  final case class ResponseSuccessWithPayload(user: User) extends ResponseBody
  final case class ResponseUpdateExisting(user: User) extends ResponseBody
  final case class UsersResult(message: String, users: Seq[User]) extends ResponseBody

  case class AuthenticationToken(value: String)
  case class UserPrincipal(name: String, token: AuthenticationToken) extends ResponseBody

  final case class ActionPerformed(description: String)

  def apply(): Behavior[Command] = registry(Set.empty)

  def selectUsers(query: UsersQuery, users: Seq[User]) = {
    val filteredUsers = query.name match {
      case None => users
      case Some(name) => users.filter(user => user.name.equalsIgnoreCase(name))
    }
    query.limit match {
      case None => filteredUsers
      case Some(max) => filteredUsers.take(max)
    }
  }

  private def registry(users: Set[User]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetUsers(query: UsersQuery, replyTo) =>
        val selected = selectUsers(query, users.toSeq)
        replyTo ! Users(selected)
        Behaviors.same
      case DeleteUser(query: UsersQuery, replyTo) =>
        replyTo ! Users(users.filter(_.name == query.name.get).toSeq)
        registry(users.filterNot(_.name == query.name.get))
      case CreateUser(user, replyTo) =>
        replyTo ! Users(List(user))
        registry(users + user)
    }
}
//#user-registry-actor
