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
    <nk-def-card>
        <nk-form :edit="editMode" :col="1">
            <nk-form-item title="计算公式">
                <nk-sp-el-view :el="def.mtSpEL"></nk-sp-el-view>
                <nk-sp-el-editor slot="edit" v-model="def.mtSpEL"></nk-sp-el-editor>
            </nk-form-item>
            <nk-form-item title="计算选项">
                <nk-sp-el-view :el="def.mtOptions" :is-json="true"></nk-sp-el-view>
                <nk-sp-el-template-editor slot="edit" v-model="def.mtOptions"></nk-sp-el-template-editor>
            </nk-form-item>
        </nk-form>
    </nk-def-card>
</template>

<script>
    import MixinDef from "MixinDef";
    export default {
        mixins:[new MixinDef({})],
    }
</script>

<style scoped>

</style>