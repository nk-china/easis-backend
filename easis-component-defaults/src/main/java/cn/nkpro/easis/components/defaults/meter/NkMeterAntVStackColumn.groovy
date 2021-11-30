package cn.nkpro.easis.components.defaults.meter


import cn.nkpro.easis.co.meter.NkAbstractEqlMeter
import org.springframework.stereotype.Component

@Component("NkMeterAntVStackColumn")
class NkMeterAntVStackColumn extends NkAbstractEqlMeter {

    @Override
    String getName() {
        return "堆叠柱状图"
    }
}
