package cn.nkpro.easis.components.defaults.cards

import cn.nkpro.easis.annotation.NkNote
import cn.nkpro.easis.docengine.NkAbstractCard
import cn.nkpro.easis.docengine.model.DocDefIV
import cn.nkpro.easis.docengine.model.DocHV
import cn.nkpro.easis.docengine.model.NkCardFormDefI
import cn.nkpro.easis.docengine.utils.NkFormCardHelper
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Deprecated
@SuppressWarnings("unused")
@NkNote("基础表单")
@Component("NkCardForm")
class NkCardForm extends NkAbstractCard<Map,NkCardFormDef> {

    @Autowired
    private NkFormCardHelper nkFormCardHelper

    @Override
    Map afterCreate(DocHV doc, DocHV preDoc, Map data, DocDefIV defIV, NkCardFormDef d) {
        return nkFormCardHelper.execSpEL(doc, data, defIV, d.getItems(), true, true)
    }

    @Override
    Map afterGetData(DocHV doc, Map data, DocDefIV defIV, NkCardFormDef d) {
        return nkFormCardHelper.execSpEL(doc, data, defIV, d.getItems(), false, true)
    }

    @Override
    Map calculate(DocHV doc, Map data, DocDefIV defIV, NkCardFormDef d, boolean isTrigger, Object options) {
        return nkFormCardHelper.execSpEL(doc, data, defIV, d.getItems(), false, true)
    }

    @Override
    Map random(DocHV docHV, DocDefIV defIV, NkCardFormDef d) {
        return nkFormCardHelper.random(d.getItems())
    }



    @JsonIgnoreProperties(ignoreUnknown=true)
    static class NkCardFormDef {
        private int col
        private List<NkCardFormDefI> items = new ArrayList<>()

        int getCol() {
            return col
        }

        void setCol(int col) {
            this.col = col
        }

        List<NkCardFormDefI> getItems() {
            return items
        }

        void setItems(List<NkCardFormDefI> items) {
            this.items = items
            if(this.items == null){
                this.items = new ArrayList<>()
            }
        }
    }
}
