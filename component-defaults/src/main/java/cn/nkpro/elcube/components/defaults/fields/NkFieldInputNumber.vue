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
    <span v-if="!editMode">
      <template v-if="value !== undefined">{{value | nkNumber(inputOptions.format||'0.00')}}</template>
      <span v-else class="empty"></span>
    </span>
    <a-select size="small"
              v-else-if="options"
              v-model="val"
              mode="default"
              :options="options"
              @change="selectChange">
    </a-select>
    <a-input-number size="small"
                    v-else
                    v-model="val"
                    @change="change"
                    @blur="blur"

                    :max="inputOptions.max"
                    :min="inputOptions.min"
                    :precision="inputOptions.digits||0"
                    :step="inputOptions.step||1"
    ></a-input-number>
</template>

<script>
    export default {
        props:{
            value: {},
            editMode: Boolean,
            inputOptions: {
                type:Object,
                default(){
                    return {}
                }
            }
        },
        data(){
            return {
                changed: undefined,
            }
        },
        computed:{
            options(){
                if(this.inputOptions && this.inputOptions.options){
                    return this.inputOptions.options.map(i=>{
                        return {key:i,label:i}
                    });
                }
            },
            val:{
                get(){
                    return this.value;
                },
                set(value){
                    this.$emit('input',value);
                }
            }
        },
        methods:{
            change(){
                this.changed = true;
            },
            blur(){
                if(this.changed){
                    this.changed = false
                    this.$emit('change',{});
                }
            },
            selectChange(){
                this.change();
                this.blur();
            }
        }
    }
</script>

<style scoped>

</style>