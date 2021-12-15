// GLOBAL

/* global window, document */

'use strict';



import $ from 'jquery';
import './component.google-analytics';
import './component.payment';

import feedback from './component.feedback';
import ToggleLink from './component.toggle-link';
import UpdateHistory from './component.update-history';

import '../../../node_modules/@scottish-government/pattern-library/src/all';

const global = {
    svgFallback: function () {
        if (!document.implementation.hasFeature('http://www.w3.org/TR/SVG11/feature#Image', '1.1')) {
            $('img[src$=".svg"]').each(function () {
                $(this).attr('src', $(this).attr('src').replace(/\.svg$/, '.png'));
            });
        }
    },

    initPubsub: function () {
        const o = $({});

        $.subscribe = function() {
            o.on.apply(o, arguments);
        };

        $.unsubscribe = function() {
            o.off.apply(o, arguments);
        };

        $.publish = function() {
            o.trigger.apply(o, arguments);
        };

        window.pubsub = $;
    },


    init: function () {
        document.documentElement.classList.add('js-enabled');

        feedback.init();
        this.initPubsub();
        this.svgFallback();
        this.setInitialCookiePermissions();
        this.initDesignSystemComponents();
        this.addTracking();
    },

    addTracking: function () {
        if (window.DS.tracking) {
            window.DS.tracking.add.backtotop = function (scope = document) {
                const backToTops = [].slice.call(scope.querySelectorAll('.ds_back-to-top__button'));
                backToTops.forEach(backToTop => {
                    if (!backToTop.classList.contains('js-has-tracking-event')) {
                        backToTop.addEventListener('click', () => {
                            window.dataLayer = window.dataLayer || [];
                            window.dataLayer.push({
                                event: 'backToTop',
                                scrollDepthAbs: window.scrollY,
                                scrollDepthRel: +(window.scrollY / window.innerHeight).toFixed(3)
                            });
                        });
                        backToTop.classList.add('js-has-tracking-event');
                    }
                });
                window.DS.tracking.add.backToTop(scope);
            };

            window.DS.tracking.init();
        }
    },

    initDesignSystemComponents: function () {
        const backToTopEl = document.querySelector('[data-module="ds-back-to-top"]');
        if (backToTopEl) {
            const backToTop = new window.DS.components.BackToTop(backToTopEl);
            backToTop.init();
        }

        // need to preprocess accordion items to group them
        const accordionItems = [].slice.call(document.querySelectorAll('.ds_accordion-item'));
        const groups = [];
        let groupItems = [];
        accordionItems.forEach(accordionItem => {
            if(accordionItem.parentNode.classList.contains('ds_accordion')) {
                return;
            }
            groupItems.push(accordionItem);
            if (!(accordionItem.nextElementSibling && accordionItem.nextElementSibling.classList.contains('ds_accordion-item'))) {
                groups.push(groupItems);
                groupItems = [];
            }
        });
        groups.forEach(groupItems => {
            const wrapper = document.createElement('div');
            wrapper.classList.add('ds_accordion');
            wrapper.dataset.module = 'ds-accordion';

            if (groupItems.length > 1) {
                const openAllButton = document.createElement('button');
                openAllButton.setAttribute('class', 'ds_link  ds_accordion__open-all  js-open-all');
                openAllButton.innerHTML = 'Open all <span class="visually-hidden">sections</span>';
                wrapper.appendChild(openAllButton);
            }

            groupItems[0].parentNode.insertBefore(wrapper, groupItems[0]);
            groupItems.forEach(item => {
                wrapper.appendChild(item);
            });
        });

        const accordions = [].slice.call(document.querySelectorAll('[data-module="ds-accordion"]'));
        accordions.forEach(accordion => new window.DS.components.Accordion(accordion).init());

        const cookieNotificationEl = document.querySelector('[data-module="ds-cookie-notification"]');
        if (cookieNotificationEl) {
            const cookieNotification = new window.DS.components.CookieNotification(cookieNotificationEl);
            cookieNotification.init();
        }

        // this one is handled differently because it applies an event to the whole body and we only want that event once
        const hidePageButtons = [].slice.call(document.querySelectorAll('.ds_hide-page'));
        if (hidePageButtons.length) {
            const hidePage = new window.DS.components.HidePage();
            hidePage.init();
        }

        const mobileMenus = [].slice.call(document.querySelectorAll('[data-module="ds-mobile-navigation-menu"]'));
        mobileMenus.forEach(mobileMenu =>  new window.DS.components.MobileMenu(mobileMenu).init());

        const notificationBanners = [].slice.call(document.querySelectorAll('[data-module="ds-notification"]'));
        notificationBanners.forEach(notificationBanner => new window.DS.components.NotificationBanner(notificationBanner).init());

        const searchBoxes = [].slice.call(document.querySelectorAll('[data-module="ds-site-search"]'));
        searchBoxes.forEach(searchBox => new window.DS.components.CollapsibleSearchBox(searchBox).init());

        const sideNavigations = [].slice.call(document.querySelectorAll('[data-module="ds-side-navigation"]'));
        sideNavigations.forEach(sideNavigation => new window.DS.components.SideNavigation(sideNavigation).init());

        const tables = [].slice.call(document.querySelectorAll('table[data-smallscreen]'));
        if (tables.length) {
            const mobileTables = new window.DS.components.MobileTables();
            mobileTables.init();
        }

        const updateHistory = document.querySelector('[data-module="gov-update-history"]');
        if (updateHistory) {
            const updateHistoryModule = new UpdateHistory(updateHistory);
            updateHistoryModule.init();
        }

        const toggleLinks = [].slice.call(document.querySelectorAll('[data-module="gov-toggle-link"]'));
        toggleLinks.forEach(toggleLink => new ToggleLink(toggleLink).init());

    },

    setInitialCookiePermissions: function () {
        const permissionsString = storage.getCookie('cookiePermissions') || '';

        if (!storage.isJsonString(permissionsString)) {
            const permissions = {};
            permissions.statistics = true;
            permissions.preferences = true;

            storage.setCookie(storage.categories.necessary,
                'cookiePermissions',
                JSON.stringify(permissions)
            );
        }
    }
};

global.init();

export default global;
