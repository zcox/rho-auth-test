package app

import cats.Monad
import cats.data.{Kleisli, OptionT}
import cats.implicits._
import cats.effect.{Effect, IO}
import fs2.{Stream, StreamApp}
import fs2.StreamApp.ExitCode
import org.http4s.{Request, /*Response, Status,*/ HttpService}
import org.http4s.server.AuthMiddleware
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.rho.{RhoService, RhoMiddleware, AuthedContext}
import org.http4s.rho.swagger.{SwaggerSyntax, SwaggerSupport}
import scala.concurrent.ExecutionContext
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

case class User(userId: String)

object Service {
  def rhoService[F[_]: Monad : Logger](authedContext: AuthedContext[F, User]): RhoService[F] = new RhoService[F] with SwaggerSyntax[F] {
    "The hello endpoint" **
    GET / "hello" / pathVar[String]("name", "Name to say hello to.") >>> authedContext.auth |>> {
      (request: Request[F], name: String, user: User) =>
        val ot = OptionT.some[F]("wtf")
        for {
          x <- ot.value
          _ <- Logger[F].info(s"$x Incoming request from $user for $name: $request")
          // r <- NotFound(s"Could not find $name")
          r <- x.fold(NotFound.pure(s"Could not find $name"))(_ => Ok.pure(s"Hello, $name."))
          // r <- x.fold(NotFound(s"Could not find $name"))(_ => Ok(s"Hello, $name."))
          // r <- x.map(_ => Ok(s"Hello, $name.")).getOrElse(NotFound("oops"))
        // } yield x.fold(Response[F](Status.NotFound))(_ => Response[F](Status.Ok))
      } yield r
    }
  }
}

abstract class App[F[_]: Effect](implicit ec: ExecutionContext) extends StreamApp[F] {

  implicit def log: Logger[F] = Slf4jLogger.unsafeFromClass(this.getClass)

  val authUser: Kleisli[OptionT[F, ?], Request[F], User] = 
    Kleisli(r => OptionT.fromOption(r.params.get("userId").map(User(_))))

  val authMiddleware: AuthMiddleware[F, User] = AuthMiddleware(authUser)

  val authedContext: AuthedContext[F, User] = new AuthedContext[F, User]

  val swaggerMiddleware: RhoMiddleware[F] = SwaggerSupport[F].createRhoMiddleware()

  val service: HttpService[F] = authMiddleware(authedContext.toService(Service.rhoService[F](authedContext).toService(swaggerMiddleware)))

  override def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] =
    BlazeBuilder[F]
      .bindHttp(8080, "0.0.0.0")
      .mountService(service, "/")
      .serve
}

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App[IO]
