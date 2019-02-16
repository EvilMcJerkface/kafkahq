<#-- @ftlvariable name="clusterId" type="java.lang.String" -->
<#-- @ftlvariable name="basePath" type="java.lang.String" -->
<#-- @ftlvariable name="compatibilityLevel" type="java.util.List<java.lang.String>" -->

<#macro table schemas isVersion>
    <#-- @ftlvariable name="schemas" type="java.util.List<org.kafkahq.models.Schema>" -->
    <div class="table-responsive">
        <table class="table table-bordered table-striped table-hover mb-0">
            <thead class="thead-dark">
                <tr>
                    <th class="text-nowrap">Id</th>
                    <#if isVersion == false>
                        <th class="text-nowrap">Subject</th>
                    </#if>
                    <th class="text-nowrap">Version</th>
                    <#if isVersion == false>
                        <th class="khq-row-action"></th>
                    </#if>
                    <th class="khq-row-action"></th>
                </tr>
            </thead>
            <tbody>
                <#if schemas?size == 0>
                    <tr>
                        <td colspan="5">
                            <div class="alert alert-info mb-0" role="alert">
                                No schema available
                            </div>
                        </td>
                    </tr>
                </#if>
                <#list schemas as schema>
                    <tr>
                        <td>${schema.getId()?c}</td>
                        <#if isVersion == false>
                            <td>${schema.getSubject()}</td>
                        </#if>
                        <td><span class="badge badge-info">${schema.getVersion()!}</span></td>
                        <#if isVersion == false>
                            <td class="khq-row-action khq-row-action-main">
                                <a href="${basePath}/${clusterId}/schema/${schema.getSubject()}"><i class="fa fa-search"></i></a>
                            </td>
                            <td class="khq-row-action">
                                <a
                                        href="${basePath}/${clusterId}/schema/${schema.getSubject()}/delete"
                                        data-confirm="Do you want to delete schema: <code>${schema.getSubject()}</code> ?"
                                ><i class="fa fa-trash"></i></a>
                            </td>
                        <#else>
                            <td class="khq-row-action">
                                <a
                                        href="${basePath}/${clusterId}/schema/${schema.getSubject()}/version/${schema.getVersion()}/delete"
                                        data-confirm="Do you want to delete version: <code>${schema.getVersion()} from ${schema.getSubject()}</code> ?"
                                ><i class="fa fa-trash"></i></a>
                            </td>
                        </#if>

                    </tr>
                    <tr>
                        <td colspan="5">
                            <button type="button" class="close d-none" aria-label="Close">
                                <span aria-hidden="true">&times;</span>
                            </button>
                            <pre class="mb-0 khq-data-highlight"><code>${schema.getSchema()}</code></pre>
                        </td>
                    </tr>
                </#list>
            </tbody>
        </table>
    </div>
</#macro>

<#macro form config schema="">
    <#-- @ftlvariable name="schema" type="org.kafkahq.models.Schema" -->
    <#-- @ftlvariable name="config" type="org.kafkahq.models.Schema.Config" -->
    <form enctype="multipart/form-data" method="post" class="khq-form khq-form-config">
        <div class="form-group row">
            <label for="name" class="col-sm-2 col-form-label">Subject</label>
            <div class="col-sm-10">
                <input type="text" class="form-control" name="subject" id="subject" placeholder="Subject" value="${(schema != "")?then(schema.getSubject()!, "")}" required ${(schema != "")?then(" readonly", "")}>
            </div>
        </div>

        <div class="form-group row">
            <label for="compatibility-level" class="col-sm-2 col-form-label">Compatibility Level</label>
            <div class="col-sm-10">
                <select class="form-control" name="compatibility-level" id="compatibility-level">
                    <option></option>
                    <#list compatibilityLevel as level>
                        <option ${(config.getCompatibilityLevel() == level)?then("selected", "")}>${level}</option>
                    </#list>
                </select>
            </div>
        </div>

        <div class="form-group row">
            <label for="schema" class="col-sm-2 col-form-label">${(schema != "")?then("Latest ", "")}Schema</label>
            <div class="col-sm-10">
                <div class="khq-ace-editor" data-type="json">
                    <div></div>
                    <textarea class="form-control" name="schema" id="schema" placeholder="Schema">${(schema != "")?then(schema.getSchema()!, "")}</textarea>
                </div>
            </div>
        </div>

        <div class="khq-submit">
            <button type="submit" class="btn btn-primary">${(schema != "")?then("Update", "Create")}</button>
        </div>
    </form>
</#macro>