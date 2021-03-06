package sample

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Import

/**
 * アプリケーションプロセスの起動クラス。
 * <p>本クラスを実行する事でSpringBootが提供する組込Tomcatでのアプリケーション起動が行われます。
 */
@SpringBootApplication
@EnableCaching(proxyTargetClass = true)
@Import(Array(classOf[ApplicationConfig], classOf[ApplicationDbConfig], classOf[ApplicationSecurityConfig]))
class Application

object Application {
  def main(args: Array[String]) {
    SpringApplication.run(classOf[Application], args: _*)
  }
}
