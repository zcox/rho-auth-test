package app

import cats.Monad
import cats.data.{Kleisli, OptionT}
import cats.implicits._
import cats.effect.{Effect, IO}
import fs2.{Stream, StreamApp}
import fs2.StreamApp.ExitCode
import org.http4s.{Request, HttpService}
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
        for {
          _ <- Logger[F].info(s"Incoming request from $user: $request")
          r <- Ok(s"Hello, $name.")
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
