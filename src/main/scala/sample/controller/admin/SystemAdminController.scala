package sample.controller.admin

import java.time.LocalDate

import scala.beans.BeanProperty

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation._

import javax.validation.Valid
import javax.validation.constraints.NotNull
import sample._
import sample.context._
import sample.context.actor._
import sample.context.audit._
import sample.context.orm.PagingList
import sample.controller._
import sample.model.constraints._
import sample.usecase.SystemAdminService
import java.beans.SimpleBeanInfo

/**
 * システムに関わる社内のUI要求を処理します。
 */
@RestController
@RequestMapping(Array("/api/admin/system"))
class SystemAdminController extends ControllerSupport {
  
  @Autowired
  private var service: SystemAdminService = _
  
  /** 利用者監査ログを検索します。 */
  @GetMapping(Array("/audit/actor/"))
  def findAuditActor(@Valid p: FindAuditActorParam): PagingList[AuditActor] =
    service.findAuditActor(p.convert)

  /** イベント監査ログを検索します。 */
  @GetMapping(Array("/audit/event/"))
  def findAuditEvent(@Valid p: FindAuditEventParam): PagingList[AuditEvent] =
    service.findAuditEvent(p.convert)
    
  /** アプリケーション設定一覧を検索します。 */
  @GetMapping(Array("/setting/"))
  def findAppSetting(@Valid p: FindAppSettingParam): Seq[AppSetting] =
    service.findAppSetting(p.convert)

  /** アプリケーション設定情報を変更します。 */
  @PostMapping(Array("/setting/{id}"))
  def changeAppSetting(@PathVariable id: String, value: String): ResponseEntity[Void] =
    resultEmpty(service.changeAppSetting(id, value))
}

/** FindAuditActorのUI変換パラメタ */
@SimpleBeanInfo
class FindAuditActorParam {
  @IdStrEmpty
  @BeanProperty
  var actorId: String = _
  @CategoryEmpty
  @BeanProperty
  var category: String = _
  @DescriptionEmpty
  @BeanProperty
  var keyword: String = _
  @NotNull
  @BeanProperty
  var roleType: String = "USER"
  @BeanProperty
  var statusType: String = _
  @ISODate
  @BeanProperty
  var fromDay: LocalDate = _
  @ISODate
  @BeanProperty
  var toDay: LocalDate = _
  @NotNull
  @BeanProperty
  var page: PaginationParam = new PaginationParam()
  def convert: FindAuditActor =
    FindAuditActor(Option(actorId), Option(category), Option(keyword),
        ActorRoleType.withName(roleType),
        Option(statusType).map(ActionStatusType.withName(_)),
        fromDay, toDay, page.convert)
}

/** FindAuditEventのUI変換パラメタ */
@SimpleBeanInfo
class FindAuditEventParam {
  @CategoryEmpty
  @BeanProperty
  var category: String = _
  @DescriptionEmpty
  @BeanProperty
  var keyword: String = _
  @BeanProperty
  var statusType: String = _
  @ISODate
  @BeanProperty
  var fromDay: LocalDate = _
  @ISODate
  @BeanProperty
  var toDay: LocalDate = _
  @NotNull
  @BeanProperty
  var page: PaginationParam = new PaginationParam()
  def convert: FindAuditEvent =
    FindAuditEvent(Option(category), Option(keyword),
        Option(statusType).map(ActionStatusType.withName(_)),
        fromDay, toDay, page.convert)
}

/** FindAppSettingのUI変換パラメタ */
@SimpleBeanInfo
class FindAppSettingParam {
  @DescriptionEmpty
  @BeanProperty
  var keyword: String = _
  def convert: FindAppSetting = FindAppSetting(Option(keyword))
}

