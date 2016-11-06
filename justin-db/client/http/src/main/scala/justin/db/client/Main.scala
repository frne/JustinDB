package justin.db.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import justin.db.storage.InMemStorage
import justin.db.{StorageNodeActor, StorageNodeActorId}

object Main extends App {
  val config = ConfigFactory.parseString(s"akka.cluster.roles = [${StorageNodeActor.role}]")
    .withFallback(ConfigFactory.load())

  implicit val system       = ActorSystem("justin-db-cluster-system", config)
  implicit val executor     = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val logger = Logging(system, getClass)

  val storageNodeActorRef = {
    val nodeId                = StorageNodeActorId(config.getInt("node.id"))
    val storageNodeActorProps = StorageNodeActor.props(nodeId, new InMemStorage())
    system.actorOf(storageNodeActorProps, name = StorageNodeActor.name(nodeId))
  }

  val router = new StorageNodeRouter(new HttpStorageNodeClient(storageNodeActorRef))

  Http()
    .bindAndHandle(router.routes, config.getString("http.interface"), config.getInt("http.port"))
    .map { binding => logger.info(s"HTTP server started at ${binding.localAddress}") }
    .recover { case ex => logger.error(ex, "Could not start HTTP server") }
}