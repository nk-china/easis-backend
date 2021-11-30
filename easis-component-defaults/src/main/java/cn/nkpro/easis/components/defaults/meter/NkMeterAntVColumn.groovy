package cn.nkpro.easis.components.defaults.meter


import cn.nkpro.easis.co.meter.NkAbstractEqlMeter
import org.springframework.stereotype.Component

@Component("NkMeterAntVColumn")
class NkMeterAntVColumn extends NkAbstractEqlMeter {

    @Override
    String getName() {
        return "柱状图"
    }
}
