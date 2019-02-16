<#-- @ftlvariable name="clusterId" type="java.lang.String" -->
<#-- @ftlvariable name="topics" type="java.util.ArrayList<org.kafkahq.models.Topic>" -->
<#-- @ftlvariable name="basePath" type="java.lang.String" -->

<#import "includes/template.ftl" as template>
<#import "includes/functions.ftl" as functions>

<@template.header "Topics", "topic" />

<#include "blocks/navbar-search.ftl" />

<div class="table-responsive">
    <table class="table table-bordered table-striped table-hover mb-0">
        <thead class="thead-dark">
            <tr>
                <th colspan="3">Topics</th>
                <th colspan="1">Partitions</th>
                <th colspan="2">Replications</th>
                <th>Consumers Groups</th>
                <th colspan="2" class="khq-row-action"></th>
            </tr>
        </thead>
        <thead class="thead-dark">
            <tr>
                <th class="text-nowrap">Name</th>
                <th class="text-nowrap">Size</th>
                <th class="text-nowrap">Weight</th>
                <th class="text-nowrap">Total</th>
                <!--
                <th class="text-nowrap">Available</th>
                <th class="text-nowrap">Under replicated</th>
                -->
                <th class="text-nowrap">Factor</th>
                <th class="text-nowrap">In Sync</th>
                <th class="text-nowrap">Consumer Groups</th>
                <th colspan="2" class="khq-row-action"></th>
            </tr>
        </thead>
        <tbody>
                <#if topics?size == 0>
                    <tr>
                        <td colspan="9">
                            <div class="alert alert-info mb-0" role="alert">
                                No topic available
                            </div>
                        </td>
                    </tr>
                </#if>
                <#list topics as topic>
                    <tr>
                        <td>${topic.getName()}</td>
                        <td>
                            <span class="text-nowrap">
                                ≈ ${topic.getSize()}
                            </span>
                        </td>
                        <td>${functions.filesize(topic.getLogDirSize())}</td>
                        <td>${topic.getPartitions()?size}</td>
                        <td>${topic.getReplicas()?size}</td>
                        <td>${topic.getInSyncReplicas()?size}</td>
                        <td>
                            <#list topic.getConsumerGroups() as group>
                                <#assign active = group.isActiveTopic(topic.getName()) >
                                <a href="${basePath}/${clusterId}/group/${group.getId()}" class="btn btn-sm mb-1 btn-${active?then("success", "warning")} ">
                                    ${group.getId()}
                                    <span class="badge badge-light">
                                        Lag: ${group.getOffsetLag(topic.getName())}
                                    </span>
                                </a><br/>
                            </#list>
                        </td>
                        <td class="khq-row-action khq-row-action-main">
                            <a href="${basePath}/${clusterId}/topic/${topic.getName()}" ><i class="fa fa-search"></i></a>
                        </td>
                        <td class="khq-row-action">
                            <#if topic.isInternal() == false>
                                <a
                                    href="${basePath}/${clusterId}/topic/${topic.getName()}/delete"
                                    data-confirm="Do you want to delete topic: <code>${topic.getName()}</code> ?"
                                ><i class="fa fa-trash"></i></a>
                            </#if>
                        </td>
                    </tr>
                </#list>
        </tbody>
    </table>
</div>

<@template.bottom>
    <a href="${basePath}/${clusterId}/topic/create" type="submit" class="btn btn-primary">Create a topic</a>
</@template.bottom>

<@template.footer/>