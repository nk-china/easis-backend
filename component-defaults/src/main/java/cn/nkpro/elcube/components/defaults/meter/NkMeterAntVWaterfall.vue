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
    <nk-meter ref="meter" :title="configEdit.title||title" :editable="editable" :enable-setting="true" @setting-submit="update">
        <div ref="container" style="height: 100%;width: 100%;"></div>
        <div slot="setting">
            <nk-form :col="1" :edit="true">
                <nk-form-item title="标题" :width="80">
                    <a-input slot="edit" v-model="configEdit.title"></a-input>
                </nk-form-item>
                <nk-form-item title="数据" :width="80" v-if="!defaultData">
                    <a-textarea slot="edit" v-model="configEdit.sql" rows="5" placeholder="ElasticSearch SQL OR JSON Data"></a-textarea>
                </nk-form-item>
                <nk-form-item title="X轴字段" :width="80">
                    <a-select v-model="configEdit.xField" :mode="columnDefs?'default':'tags'" :options="columnDefs"></a-select>
                </nk-form-item>
                <nk-form-item title="Y轴字段" :width="80">
                    <a-select v-model="configEdit.yField" :mode="columnDefs?'default':'tags'" :options="columnDefs"></a-select>
                </nk-form-item>
                <nk-form-item title="缩略轴" :width="80">
                    <a-switch v-model="configEdit.slider" size="small"></a-switch>
                </nk-form-item>
                <nk-form-item title="中位数" :width="80">
                    <a-switch v-model="configEdit.median" size="small"></a-switch>
                </nk-form-item>
            </nk-form>
        </div>
    </nk-meter>
</template>

<script>
    import {Waterfall} from '@antv/g2plot';

    export default {
        props:{
            editable:Boolean,
            title:String,
            value:Object,
            defaultData:Array,
            columnDefs:Array,
        },
        data(){
            return {
                g:undefined,
                configEdit:undefined,
            }
        },
        created(){
            this.configEdit = Object.assign({},this.value);
        },
        mounted(){
            if(this.configEdit.xField&&this.configEdit.yField){
                this.render(this.configEdit);
            }else{
                this.$refs.meter.setSettingMode(true);
            }
        },
        methods:{
            load(config){
                if(this.defaultData){
                    return Promise.resolve({data:this.defaultData});
                }
                if(config.sql){
                    if(config.sql.trim().startsWith("[") && config.sql.trim().endsWith("]")){
                        return new Promise((r)=>{setTimeout(()=>{r({data:JSON.parse(config.sql)});},100);});
                    }
                    return this.$http.postJSON(`/api/meter/card/get/${this.$options._componentTag}`,config);
                }
                if(config.data){
                    new Promise((r)=>{setTimeout(()=>{r({data:JSON.parse(config.data)});},100);});
                }
                return Promise.resolve({data:[]});
            },
            render(config){
                this.load(config).then(res=>{
                    const gConfig = {
                        data : res.data,
                        padding: 'auto',
                        appendPadding: [20, 0, 0, 0],
                        xField: config.xField,
                        yField: config.yField,
                        meta: {
                            month: {
                                alias: '月份',
                            },
                            value: {
                                alias: '销售量',
                                formatter: (v) => `${v / 10000000} 亿`,
                            },
                        },
                        /** 展示总计 */
                        total: {
                            label: undefined,
                        },
                        color: ({ month, value }) => {
                            if (month === '2019' || month === '2020' || month==='Total') {
                                return '#96a6a6';
                            }
                            return value > 0 ? '#f4664a' : '#30bf78';
                        },
                        legend: {
                            custom: true,
                            items: [
                                {
                                    name: '增长',
                                    value: 'increase',
                                    marker: { symbol: 'square', style: { r: 5, fill: '#f4664a' } },
                                },
                                {
                                    name: '减少',
                                    value: 'decrease',
                                    marker: { symbol: 'square', style: { r: 5, fill: '#30bf78' } },
                                },
                                {
                                    name: '合计',
                                    value: 'total',
                                    marker: { symbol: 'square', style: { r: 5, fill: '#96a6a6' } },
                                },
                            ],
                        },
                        label: {
                            style: {
                                fontSize: 10,
                            },
                            layout: [{ type: 'interval-adjust-position' }],
                            background: {
                                style: {
                                    fill: '#f6f6f6',
                                    stroke: '#e6e6e6',
                                    strokeOpacity: 0.65,
                                    radius: 2,
                                },
                                padding: 1.5,
                            },
                        },
                        waterfallStyle: () => {
                            return {
                                fillOpacity: 0.85,
                            };
                        },
                    };

                    if(this.g){
                        this.g.update(gConfig);
                    }else if(this.$refs.container){
                        this.g = new Waterfall(this.$refs.container,gConfig);
                        this.g.render();
                    }
                });
            },
            update(){
                try{
                    this.render(this.configEdit);
                    const event = Object.assign({},this.configEdit);
                    this.$emit("update:config",event);
                    this.$emit("input",event);
                }catch (e) {
                    console.error(e);
                    this.$refs.meter.setSettingMode(true);
                }
            }
        },
        destroyed() {
            if(this.g){
                this.g.destroy();
            }
        }
    }
</script>

<style scoped>

</style>