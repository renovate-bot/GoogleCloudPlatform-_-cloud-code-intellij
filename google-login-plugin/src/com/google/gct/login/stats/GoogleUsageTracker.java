/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gct.login.stats;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gct.login.stats.UsageTrackerService.UsageTracker;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Google Usage Tracker that reports to Cloud Tools Analytics backend
 */
public class GoogleUsageTracker implements UsageTracker {
    private static final Logger LOG = Logger.getInstance(GoogleUsageTracker.class);
    @NonNls
    private static final String ANALYTICS_URL = "https://ssl.google-analytics.com/collect";
    @NonNls
    private static final String ANAYLTICS_ID = "UA-67138797-1";
    @NonNls
    private static final String ANALYTICS_APP = "Cloud Tools for IntelliJ";
    @NonNls
    private static final String INTELLIJ_EDITION = "intellij.edition";

    private static final List<? extends NameValuePair> analyticsBaseData = ImmutableList
            .of(new BasicNameValuePair("v", "1"),
                    new BasicNameValuePair("tid", ANAYLTICS_ID),
                    new BasicNameValuePair("t", "event"),
                    new BasicNameValuePair("an", ANALYTICS_APP),
                    new BasicNameValuePair("av", ApplicationInfo.getInstance().getFullVersion()),
                    new BasicNameValuePair("cid", UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())));

    public void trackEvent(@NotNull String eventCategory,
                           @NotNull String eventAction,
                           @Nullable String eventLabel,
                           @Nullable Integer eventValue) {
        if(!ApplicationManager.getApplication().isUnitTestMode()) {
            if(PlatformUtils.isIntelliJ()) {
                ArrayList postData = Lists.newArrayList(analyticsBaseData);
                postData.add(new BasicNameValuePair(INTELLIJ_EDITION, PlatformUtils.isCommunityEdition() ? "community" :"ultimate"));
                postData.add(new BasicNameValuePair("ec", eventCategory));
                postData.add(new BasicNameValuePair("ea", eventAction));
                if(!StringUtil.isEmpty(eventLabel)) {
                    postData.add(new BasicNameValuePair("el", eventLabel));
                }

                if(eventValue != null) {
                    if(eventValue.intValue() < 0) {
                        LOG.debug("Attempting to send negative event value to the analytics server");
                    }

                    postData.add(new BasicNameValuePair("ev", eventValue.toString()));
                }

                sendPing(postData);
            }
        }
    }

    private static void sendPing(@NotNull final List<? extends NameValuePair> postData) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
                CloseableHttpClient client = HttpClientBuilder.create().build();
                HttpPost request = new HttpPost(ANALYTICS_URL);

                try {
                    request.setEntity(new UrlEncodedFormEntity(postData));
                    CloseableHttpResponse e = client.execute(request);
                    StatusLine status = e.getStatusLine();
                    HttpEntity entity = e.getEntity(); // throw it away, don't care, not sure if we need to read in the response?
                    if(status.getStatusCode() >= 300) {
                        GoogleUsageTracker.LOG.debug("Non 200 status code : " + status.getStatusCode() + " - " + status.getReasonPhrase());
                    }
                } catch (IOException var9) {
                    GoogleUsageTracker.LOG.debug("IOException during Analytics Ping", new Object[]{var9.getMessage()});
                } finally {
                    HttpClientUtils.closeQuietly(client);
                }

            }
        });
    }

}
