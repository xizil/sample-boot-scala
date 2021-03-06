package sample.context.security

import java.util.Locale

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition._
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.MessageSource
import org.springframework.context.annotation._
import org.springframework.http.MediaType
import org.springframework.security.authentication._
import org.springframework.security.config.annotation.web.builders._
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core._
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication._
import org.springframework.security.web.authentication.logout._
import org.springframework.web.filter._

import javax.servlet._
import javax.servlet.http._
import sample._
import sample.context.actor.ActorSession

/**
 * Spring Security(認証/認可)全般の設定を行います。
 * <p>認証はベーシック認証ではなく、HttpSessionを用いた従来型のアプローチで定義しています。
 * <p>設定はパターンを決め打ちしている関係上、既存の定義ファイルをラップしています。
 * securityプリフィックスではなくextension.securityプリフィックスのものを利用してください。
 * <p>low: HttpSessionを利用しているため、横スケールする際に問題となります。その際は上位のL/Bで制御するか、
 * SpringSession(HttpSessionの実装をRedis等でサポート)を利用する事でコード変更無しに対応が可能となります。
 * <p>low: 本サンプルでは無効化していますが、CSRF対応はプロジェクト毎に適切な利用を検討してください。
 */
class SecurityConfigurer extends WebSecurityConfigurerAdapter {

  /** Spring Boot のサーバ情報 */
  @Autowired
  var serverProps: ServerProperties = _
  /** 拡張セキュリティ情報 */
  @Autowired
  var props: SecurityProperties = _
  /** 認証/認可利用者サービス */
  @Autowired
  var actorFinder: SecurityActorFinder = _ 
  /** カスタムエントリポイント(例外対応) */
  @Autowired
  var entryPoint: SecurityEntryPoint = _
  /** ログイン/ログアウト時の拡張ハンドラ */
  @Autowired
  var loginHandler: LoginHandler = _
  /** ThreadLocalスコープの利用者セッション */
  @Autowired
  var actorSession: ActorSession = _
  /** CORS利用時のフィルタ */
  @Autowired(required = false)
  var corsFilter: CorsFilter = _
  /** 認証配下に置くServletFilter */
  @Autowired(required = false)
  var filters: SecurityFilters = _

  override def configure(web: WebSecurity) =
    web.ignoring().mvcMatchers(serverProps.getPathsArray(props.auth.ignorePath): _*)
  
  override def configure(http: HttpSecurity) {
    // Target URL
    http
      .authorizeRequests()
      .mvcMatchers(props.auth.excludesPath: _*).permitAll()
    http
      .csrf().disable()
      .authorizeRequests()
        .mvcMatchers(props.auth.pathAdmin: _*).hasRole("ADMIN")
        .mvcMatchers(props.auth.path: _*).hasRole("USER")
    // common
    http
      .exceptionHandling().authenticationEntryPoint(entryPoint)
    http
      .sessionManagement()
      .maximumSessions(props.auth.maximumSessions)
      .and()
      .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
    http
      .addFilterAfter(new ActorSessionFilter(actorSession), classOf[UsernamePasswordAuthenticationFilter])
    if (corsFilter != null)
      http.addFilterBefore(corsFilter, classOf[LogoutFilter])
    if (filters != null)
      filters.filters().foreach(http.addFilterAfter(_, classOf[ActorSessionFilter]))

    // login/logout
    http
      .formLogin().loginPage(props.auth.loginPath)
      .usernameParameter(props.auth.loginKey).passwordParameter(props.auth.passwordKey)
      .successHandler(loginHandler).failureHandler(loginHandler)
      .permitAll()
      .and()
      .logout().logoutUrl(props.auth.logoutPath)
      .logoutSuccessHandler(loginHandler)
      .permitAll();
  }
}

/**
 * Spring Securityのカスタム認証プロバイダ。
 * <p>主にパスワード照合を行っています。
 */
class SecurityProvider extends AuthenticationProvider {
  @Autowired
  private var actorFinder: SecurityActorFinder = _
  @Autowired
  @Lazy
  private var encoder: PasswordEncoder = _
  override def authenticate(authentication: Authentication):Authentication = {
    if (authentication.getPrincipal() == null || authentication.getCredentials() == null) {
        throw new BadCredentialsException("ログイン認証に失敗しました");
    }
    val service = actorFinder.detailsService
    val details = service.loadUserByUsername(authentication.getPrincipal().toString())
    val presentedPassword = authentication.getCredentials().toString()
    if (!encoder.matches(presentedPassword, details.getPassword())) {
      throw new BadCredentialsException("ログイン認証に失敗しました");
    }
    val ret =  new UsernamePasswordAuthenticationToken(
        authentication.getName(), "", details.getAuthorities());
    ret.setDetails(details);
    return ret;
  }
  override def supports(authentication: Class[_]) =
    classOf[UsernamePasswordAuthenticationToken].isAssignableFrom(authentication)
}

/**
 * Spring Securityのカスタムエントリポイント。
 * <p>API化を念頭に例外発生時の実装差込をしています。
 */
class SecurityEntryPoint extends AuthenticationEntryPoint {
  @Autowired
  var msg: MessageSource = _
  override def commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
    if (response.isCommitted()) return
    val message =
      if (authException.isInstanceOf[InsufficientAuthenticationException])
        msg.getMessage(ErrorKeys.AccessDenied, Array(), Locale.getDefault())
      else
        msg.getMessage(ErrorKeys.Authentication, Array(), Locale.getDefault())
    response.setContentType(MediaType.APPLICATION_JSON_VALUE)
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
    response.setCharacterEncoding("UTF-8")
    response.getWriter().write("{\"message\": \"" + message + "\"}");
  }
}
  
/**
 * SpringSecurityの認証情報(Authentication)とActorSessionを紐付けるServletFilter。
 * <p>dummyLoginが有効な時は常にSecurityContextHolderへAuthenticationを紐付けます。 
 */
case class ActorSessionFilter(actorSession: ActorSession) extends GenericFilterBean {
  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    val authOpt = SecurityActorFinder.authentication
    if (authOpt.isDefined && authOpt.map(_.getDetails().isInstanceOf[ActorDetails]).getOrElse(false)) {
      val details = authOpt.get.getDetails().asInstanceOf[ActorDetails]
      actorSession.bind(details.actor)
      try {
        chain.doFilter(request, response)
      } finally {
        actorSession.unbind()
      }
    } else {
      actorSession.unbind()
      chain.doFilter(request, response)  
    }
  }
}

/**
 * Spring Securityにおけるログイン/ログアウト時の振る舞いを拡張するHandler。
 */
class LoginHandler extends AuthenticationSuccessHandler with AuthenticationFailureHandler with LogoutSuccessHandler {
  @Autowired
  var props: SecurityProperties = _

  /** ログイン成功処理 */
  override def onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
    Option(authentication.getDetails().asInstanceOf[ActorDetails]).map(detail =>
      detail.bindRequestInfo(request))
    if (response.isCommitted()) return
    writeReponseEmpty(response, HttpServletResponse.SC_OK);
  }

  /** ログイン失敗処理 */
  override def onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
    if (response.isCommitted()) return
    writeReponseEmpty(response, HttpServletResponse.SC_BAD_REQUEST)
  }

  /** ログアウト成功処理 */
  override def onLogoutSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
    if (response.isCommitted()) return;
    writeReponseEmpty(response, HttpServletResponse.SC_OK);
  }
  
  private def writeReponseEmpty(response: HttpServletResponse, status: Int) {
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setStatus(status);
    response.getWriter().write("{}");      
  }    
}
