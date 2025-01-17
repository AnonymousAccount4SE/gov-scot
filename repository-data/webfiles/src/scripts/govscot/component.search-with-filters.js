// SEARCH FILTERS COMPONENT
// Contains functionality for the filterable search pages

/* global window, document */

'use strict';

import searchUtils from './search-utils';
import $ from 'jquery';
import DSDatePicker from '../../../node_modules/@scottish-government/pattern-library/src/components/date-picker/date-picker';
import GovFilters from './component.filters';
import breakpointCheck from '../../../node_modules/@scottish-government/pattern-library/src/base/utilities/breakpoint-check/breakpoint-check';

window.dataLayer = window.dataLayer || [];

function getParameterByName(name, url) {
    if (!url) url = window.location.href;
    name = name.replace(/[\[\]]/g, "\\$&");
    let regex = new RegExp("[?&]" + name + "(=([^&#]*)|&|#|$)"),
        results = regex.exec(url);
    if (!results) return null;
    if (!results[2]) return '';
    return decodeURIComponent(results[2].replace(/\+/g, " "));
}

const SearchWithFilters = function (settings) {

    this.filtersContainer = document.getElementById('filters');
    this.resultsContainer = document.querySelector('#search-results');

    this.settings = {
        maxDate: new Date(),
        minDate: new Date(1999, 5, 1)
    };

    this.settings = $.extend(this.settings, settings);

    this.attachEventHandlers = attachEventHandlers;
    this.clearErrors = clearErrors;
    this.enableJSFilters = enableJSFilters;
    this.gatherParams = gatherParams;
    this.hasActiveSearch = hasActiveSearch;
    this.initDateFilters = initDateFilters;
    this.init = init;
    this.validateDateInput = validateDateInput;
};

function init() {
    let that = this;

    if (!this.filtersContainer) {
        return;
    }

    const govFilterEl = document.querySelector('[data-module="gov-filters"]');
    this.govFilters = new GovFilters(govFilterEl);
    this.govFilters.init();

    this.searchParams = this.gatherParams(true);

    this.attachEventHandlers();
    this.enableJSFilters();
    this.initDateFilters();
    this.searchUtils = searchUtils;
    this.submitSearch = function (options) {
        options = options || {};
        if (options.changingPage) {that.isChangingPage = true;}
        if (options.popstate) {that.isPopstate = true;}
        $('#filters').submit();
    };
    updateFilterCounts(this.gatherParams());
}

function attachEventHandlers () {
    let that = this;

    $('#filters').on('submit', function (event) {
        if (!that.resultsContainer) {
            return;
        }

        event.preventDefault();

        // do not proceed if there are errors
        if (document.querySelectorAll('#filters [aria-invalid="true"]').length) {
            return false;
        }

        if (!that.isChangingPage) {that.searchParams.page = 1;}

        let currentParams = that.gatherParams();
        let newQueryString = searchUtils.getNewQueryString(currentParams);

        that.resultsContainer.classList.add('js-loading-inactive');
        that.filtersContainer.classList.add('js-loading-inactive');

        $.ajax({
            url: window.location.pathname + newQueryString
        }).done(function (response) {

            // skip this if we're on popstate
            if (that.isPopstate){
                delete that.isPopstate;
            } else {
                // update querystring
                try {
                    window.history.pushState('', '', newQueryString);
                } catch(error) {
                    // history API not supported
                }
            }

            // update results (incl pagination and status readout)
            $('#search-results').html($(response).find('#search-results').html());

            // update display status of "clear" buttons
            if (that.hasActiveSearch(currentParams)) {
                $('.js-clear-filters').removeClass('hidden');
            } else {
                $('.js-clear-filters').addClass('hidden');
            }

            // remove "loading" message
            that.resultsContainer.classList.remove('js-loading-inactive');
            that.filtersContainer.classList.remove('js-loading-inactive');

            // update count for mobile
            $('.js-search-results-count').html($('#search-results .search-results__count').html());

            updateFilterCounts(currentParams);

            that.govFilters.closeFilters();

            // scroll to the top of the page if we are changing page
            if (that.isChangingPage) {
                let pageContent = document.getElementById('main-content');
                window.scrollTo(window.scrollX, pageContent.offsetTop + pageContent.offsetParent.offsetTop);
            }
            that.isChangingPage = false;
        }).fail(function () {
            window.location.search = newQueryString;
        });
    });

    let t;

    $('.ds_field-group').on('change', 'input[type=checkbox]', function () {
        let containerType = $(this)
            .closest('.ds_accordion-item')
            .find('.ds_accordion-item__title')
            .text()
            .toLowerCase();

        dataLayer.push({
            'filter': containerType,
            'interaction': this.checked ? 'check': 'uncheck',
            'value': this.value,
            'event': 'filters'
        });

        // If on mobile don't do the search automatically.
        if (breakpointCheck('medium')) {
            clearTimeout(t);

            // do search on a small timeout to allow user to select multiple items without making multiple requests
            t = setTimeout(function() {
                delete that.searchParams.page;
                that.submitSearch();
            }, 300);
        }
    });

    // clear filters
    $('body').on('click', '.js-clear-filters', function (e) {
        e.preventDefault();

        dataLayer.push({
            'event': 'filters-clear'
        });

        // clear all filters
        const checkboxes = [].slice.call(that.filtersContainer.querySelectorAll('input[type="checkbox"]'));
        const textInputs = [].slice.call(that.filtersContainer.querySelectorAll('input[type="text"]'));

        checkboxes.forEach(element => element.checked = false);
        textInputs.forEach(element => element.value = '');

        that.clearErrors();

        delete that.searchParams.page;

        that.submitSearch();
    });

    $('#search-results').on('click', '.ds_pagination__page', function (event) {
        event.preventDefault();

        that.searchParams.page = getParameterByName('page', event.target.href);
        that.submitSearch({changingPage: true});
    });

    window.onpopstate = function () {
        that.searchParams.page = getParameterByName('page');
        that.submitSearch({
            changingPage: true,
            popstate: true
        });
    };
}

function clearErrors() {
    // clear any error states on filter fields
    // quick & dirty, will be replaced by enterprise search
    const that = this;

    const inputs = [].slice.call(that.filtersContainer.querySelectorAll('.ds_input--error'));
    const messages = [].slice.call(that.filtersContainer.querySelectorAll('.ds_question__error-message'));
    const questions = [].slice.call(that.filtersContainer.querySelectorAll('.ds_question--error'));

    inputs.forEach(element => {
        element.classList.remove('ds_input--error');
        element.removeAttribute('aria-invalid');
    });
    messages.forEach(element => element.parentNode.removeChild(element));
    questions.forEach(element => element.classList.remove('ds_question--error'));
}

function enableJSFilters () {
    [].slice.call(document.querySelectorAll('.ds_field-group--checkboxes input[type="radio"]')).forEach((item => {
        item.type = 'checkbox';
        item.classList.remove('ds_radio__input');
        item.classList.add('ds_checkbox__input');
        item.dataset.form = item.dataset.form.replace('radio-', 'checkbox-');

        const label = item.nextElementSibling;
        label.classList.remove('ds_radio__label');
        label.classList.add('ds_checkbox__label');

        const parent = item.parentNode;
        parent.classList.remove('ds_radio');
        parent.classList.remove('ds_radio--small');
        parent.classList.add('ds_checkbox');
        parent.classList.add('ds_checkbox--small');
    }));

    // populate checkboxes from searchParams
    $('.ds_field-group--checkboxes input[data-checkedonload]').prop('checked', true);

    // date pickers display
    $('.js-show-calendar').removeClass('hidden  hidden--hard');

    // filter button display
    $('#filter-actions').addClass('visible-xsmall  filter-actions');
}

function gatherParams (initial) {
    let searchParams = this.searchParams || {};

    // KEYWORD / TERM
    if (window.location.href.indexOf('/search/') !== -1) {
        searchParams.q = encodeURI($('#filters-search-term').val());
        searchParams.cat = 'sitesearch';
    } else {
        searchParams.term = encodeURI($('#filters-search-term').val());
        searchParams.cat = 'filter';
    }

    // PAGINATION
    if (initial) {
        searchParams.page = getParameterByName('page') || 1;
        searchParams.size = getParameterByName('size') || 10;
    }

    // TOPICS
    searchParams.topics = [];
    $.each( $('input[name="topics"]:checked') , function (index, checkbox) {
        searchParams.topics.push(checkbox.value);
    });
    if (searchParams.topics.length === 0) {
        delete searchParams.topics;
    }

    // PUBLICATION TYPES
    searchParams.publicationTypes = [];
    $.each( $('input[name="publicationTypes"]:checked') , function (index, checkbox) {
        searchParams.publicationTypes.push(checkbox.value);
    });
    if (searchParams.publicationTypes.length === 0) {
        delete searchParams.publicationTypes;
    }

    // DATE RANGES
    if ($('#filter-date-range').length) {
        searchParams.date = searchParams.date || {};

        searchParams.date.begin = encodeURI($('#date-from').val());
        searchParams.date.end = encodeURI($('#date-to').val());
    }

    return searchParams;
}

/**
    * Determines whether or not there is an active search
    * @param params
    * @returns {boolean}
    */
function hasActiveSearch(params) {
    let hasActiveSearch = false;

    for (let key in params) {
        if (!params.hasOwnProperty(key)) {
            continue;
        }

        let value = params[key];
        if (key === 'topics' || key === 'term' || key === 'publicationTypes') {
            if (value !== '') {
                hasActiveSearch = true;
            }
        } else if (key === 'date' && (value.begin || value.end)) {
            hasActiveSearch = true;
        }
    }

    return hasActiveSearch;
}

function initDateFilters() {
    const imagePath = document.getElementById('imagePath').value;
    const fromDatePickerElement = document.querySelector('#fromDatePicker');
    const toDatePickerElement = document.querySelector('#toDatePicker');
    const fromDatePicker = new DSDatePicker(fromDatePickerElement, {imagePath: imagePath, maxDate: new Date()});
    const toDatePicker = new DSDatePicker(toDatePickerElement, { imagePath: imagePath, maxDate: new Date()});

    fromDatePicker.init();
    toDatePicker.init();

    if (fromDatePickerElement) {
        fromDatePickerElement.addEventListener('change', () => {
            if (this.validateDateInput($(fromDatePicker.inputElement))) {
                toDatePicker.inputElement.dataset.mindate = fromDatePicker.inputElement.value;
                if (breakpointCheck('medium')) {
                    delete this.searchParams.page;
                    this.submitSearch();
                }
            }
        });
    }

    if (toDatePickerElement) {
        toDatePickerElement.addEventListener('change', () => {
            if (this.validateDateInput($(toDatePicker.inputElement))) {
                fromDatePicker.inputElement.dataset.maxdate = toDatePicker.inputElement.value;
                if (breakpointCheck('medium')) {
                    delete this.searchParams.page;
                    this.submitSearch();
                }
            }
        });
    }
}

function validateDateInput(element) {
    let isValid = true;

    const afterElement = document.getElementById('date-from');
    const beforeElement = document.getElementById('date-to');

    // 1) is the date in an allowed format?
    if (!searchUtils.validateInput(element[0], [searchUtils.dateRegex])) {
        isValid = false;
    }

    return isValid;
}

function updateFilterCounts(currentParams) {
    const publicationTypesCount = document.querySelector('.js-publication-types-count');
    const topicsCount = document.querySelector('.js-topics-count');

    if (publicationTypesCount) {
        if (currentParams.publicationTypes) {
            publicationTypesCount.dataset.count = currentParams.publicationTypes.length;
        } else {
            delete publicationTypesCount.dataset.count;
        }
    }

    if (topicsCount) {
        if (currentParams.topics) {
            topicsCount.dataset.count = currentParams.topics.length;
        } else {
            delete topicsCount.dataset.count;
        }
    }
}

export default SearchWithFilters;
