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
    <nk-def-card :title="`${card.cardName}配置`">
        <nk-form :col="1" :edit="editMode">
            <nk-form-item title="服务">
                {{def.remoteAdapter}}
                <a-select v-model="def.remoteAdapter" slot="edit" size="small" style="width:200px" :options="adapters"></a-select>
            </nk-form-item>
            <nk-form-item title="服务参数">
                {{def.remoteAdapterParamsSpEL}}
                <nk-sp-el-template-editor  slot="edit" v-model="def.remoteAdapterParamsSpEL" style="width:200px"></nk-sp-el-template-editor>
            </nk-form-item>
        </nk-form>

        <nk-standard-card-def-table v-model="def.fields" :edit-mode="editMode" />

    </nk-def-card>
</template>

<script>
    import MixinDef from "MixinDef";

    export default {
        mixins:[new MixinDef({})],
        created() {

            if(!this.def.fields)
                this.def.fields = [];

            if(!this.def.fields.find(e=>e.key==='name'))
                this.def.fields.push({
                    key: 'name',
                    label: '姓名',
                    visible : true,
                    readonly : false,
                });

            if(!this.def.fields.find(e=>e.key==='sex'))
                this.def.fields.push({
                    key: 'sex',
                    label: '性别',
                    type: 'select',
                    visible : true,
                    readonly : false,
                });

            if(!this.def.fields.find(e=>e.key==='age'))
                this.def.fields.push({
                    key: 'age',
                    label: '年龄',
                    visible : true,
                    readonly : false,
                });

            if(!this.def.fields.find(e=>e.key==='likes'))
                this.def.fields.push({
                    key: 'likes',
                    label: '爱好',
                    visible : true,
                    readonly : true,
                });
        },
        data(){
            return {
                adapters:[]
            }
        },
        mounted(){
            this.nk$callDef("listedAdapters").then(res=>{
                this.adapters = res;
            });
        }
    }
</script>

<style scoped>

</style>