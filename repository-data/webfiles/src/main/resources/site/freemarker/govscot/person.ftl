<#include "../include/imports.ftl">

<#if document??>
    <article id="page-content">
        <@hst.manageContent hippobean=document/>
        <#if document.contactInformation??>
            <#assign contactInformation = document.contactInformation/>
        </#if>
        <#if document.postalAddress??>
            <#assign postalAddress = document.postalAddress/>
        </#if>

        <div class="grid"><!--
            --><div class="grid__item medium--eight-twelfths">
                <header class="article-header">
                    <h1 class="article-header__title">${document.title}</h1>

                    <p class="article-header__subtitle">${document.roleTitle}</p>
                </header>
            </div><!--
        --></div>

        <div class="grid"><!--
            --><div class="grid__item medium--four-twelfths large--three-twelfths">

                <div class="person person--bordered-mobile">
                    <div class="person person--bordered-mobile">
                        <div class="person__image-container person__image-container--centred-mobile">
                            <img class="person__image" src="/site/assets/images/people/placeholder.png" alt="" />
                        </div>
                    </div>
                </div>

                <div class="hidden-xsmall">
                    <#include 'common/contact-information.ftl' />
                </div>

            </div><!--
            --><div class="grid__item medium--eight-twelfths large--seven-twelfths">

                <div class="body-content">

                    <div class="leader leader--first-para">
                        ${document.content.content}
                    </div>

                    <div class="visible-xsmall">
                        <#include 'common/contact-information.ftl' />
                    </div>
                </div>
                <!-- /end .body-content -->
            </div><!--
            --><div class="grid__item medium--three-twelfths large--two-twelfths">
                <!-- RIGHT SIDE (BLANK) -->
            </div><!--
        --></div>

    </article>
<#-- @ftlvariable name="editMode" type="java.lang.Boolean"-->
<#elseif editMode>
  <div>
    <img src="<@hst.link path="/images/essentials/catalog-component-icons/simple-content.png" />"> Click to edit Content
  </div>
</#if>