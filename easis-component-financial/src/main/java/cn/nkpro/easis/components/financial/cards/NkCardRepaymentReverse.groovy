package cn.nkpro.easis.components.financial.cards

import cn.nkpro.easis.annotation.NkNote
import cn.nkpro.easis.docengine.NkAbstractCard
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

// todo 待开发卡片
@Order(10004)
@NkNote("账单红冲")
@Component("NkCardRepaymentReverse")
class NkCardRepaymentReverse extends NkAbstractCard<Map,Map> {
}
