<!--
	This file is part of ELCube.
	ELCube is free software: you can redistribute it and/or modify
	it under the terms of the GNU Affero General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	ELCube is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU Affero General Public License for more details.
	You should have received a copy of the GNU Affero General Public License
	along with ELCube.  If not, see <https://www.gnu.org/licenses/>.
-->
<template>
    <nk-form ref="form" :col="2" :edit="editMode">
        <nk-form-item term="单据类型">
            <span class="nk-text-ellipsis">{{doc.def && doc.def.docType}} | {{doc.def && doc.def.docName}}</span>
        </nk-form-item>
        <nk-form-item term="交易伙伴">
            <router-link v-if="doc.partnerId" :to="`/apps/docs/detail/${doc.partnerId}`">{{doc.partnerName}}</router-link>
            <span v-else-if="doc.partnerName">{{doc.partnerName}}</span>
            <span v-else style="color: rgba(0, 0, 0, 0.45);">&lt;未选择&gt;</span>
        </nk-form-item>
        <nk-form-item term="单据编号">
            <span v-if="doc.docNumber">{{doc.docNumber}}</span>
            <span v-else style="color: rgba(0, 0, 0, 0.45);">&lt;未编号&gt;</span>
        </nk-form-item>
        <nk-form-item term="单据描述"
                      :validateFor="doc.docName"
                      :message="`请输入单据描述`"
                      len
                      :max="20"
                      :lenMessage="`单据描述1-20个字`">
            {{doc.docName}}
            <a-input v-model="doc.docName" slot="edit" allowClear size="small" ></a-input>
        </nk-form-item>
        <nk-form-item term="创建时间">{{doc.createdTime | nkDatetimeFriendly}}</nk-form-item>
        <nk-form-item term="更新时间">{{doc.updatedTime | nkDatetimeFriendly}}</nk-form-item>
        <nk-form-item term="备注" :col="2">
            {{doc.docDesc||'暂无内容'}}
            <a-textarea v-model="doc.docDesc" slot="edit" :auto-size="{ minRows: 3, maxRows: 10 }"></a-textarea>
        </nk-form-item>
    </nk-form>
</template>

<script>
    import Mixin from "Mixin";

    export default {
        mixins:[new Mixin()],
        data(){
            return {
            }
        },
        created() {
        },
        methods:{
            hasError(){
                return this.$refs.form.hasError()
            }
        }
    }
</script>

<style scoped>

</style>

<i18n>
    {
    "en": {
    "hello": "hello world!"
    },
    "zh_CN": {
    "hello": "你好，世界！"
    }
    }
</i18n>

<docs>
    - 这是一段文档
</docs>