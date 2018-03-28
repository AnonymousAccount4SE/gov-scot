<#include "../../include/imports.ftl">

<div class="body-content">
    <div class="page-group__content body-content inner-shadow-top inner-shadow-top--no-desktop">
    <#if document != index>
        <h2>${document.title}</h2>
    </#if>

    ${document.content.content}

    <#if document.actions??>
        <h3>Actions</h3>
    ${document.actions}
    </#if>

    <#if document.background??>
        <h3>Background</h3>
    ${document.background}
    </#if>

    <#if document.billsAndLegislation != ''>
        <h3>Bills and legislation</h3>
    ${document.billsAndLegislation}
    </#if>

    <#if document.contact??>
        <h3>Contact</h3>
    ${document.contact}
    </#if>
    </div>

    <nav class="multipage-nav visible-xsmall">
        <div class="grid"><!--
               -->
            <div class="grid__item small--six-twelfths push--small--six-twelfths">
            <#if next??>
                <div class="multipage-nav__container">
                    <@hst.link var="link" hippobean=next/>
                    <a class="multipage-nav__link multipage-nav__link--next" href="${link}">
                    ${next.title} <span
                            class="multipage-nav__icon multipage-nav__icon--right fa fa-chevron-right fa-2x"></span></a>
                </div>
            </#if>
            </div><!--

                         -->
            <div class="grid__item small--six-twelfths pull--small--six-twelfths">
            <#if prev??>
                <div class="multipage-nav__container">
                    <@hst.link var="link" hippobean=prev/>
                    <a class="multipage-nav__link multipage-nav__link--previous" href="${link}"><span
                            class="fa fa-2x fa-chevron-left multipage-nav__icon multipage-nav__icon--left"></span>
                    ${prev.title} </a>
                </div>
            </#if>
            </div><!--
     --></div>
    </nav>
</div>