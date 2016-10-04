/*
 * Copyright 2014 Igor Almeida.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */

package com.example.muzei.muzeiapod;

import android.content.Intent;
import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.Date;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

public class ApodNasaArtSource extends RemoteMuzeiArtSource {
    private static final String TAG = "ApodNasaforMuzei";
    private static final String SOURCE_NAME = "ApodNasaSource";

    private static final int ROTATE_TIME_MILLIS = 24 * 60 * 60 * 1000; // rotate every 24 hours

    public ApodNasaArtSource() {
        super(SOURCE_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setUserCommands(BUILTIN_COMMAND_ID_NEXT_ARTWORK);
    }

    public Document getURLDocument(URL url) {
        Document d;
        try {
            d = Jsoup.parse(url, 20*1000); /* ms timeout */
        } catch (IOException e) {
            return null;
        }
        return d;
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {
        URI topUri;
        try {
            topUri = new URI("http://apod.nasa.gov/");
        } catch (URISyntaxException e) {
            return;
        }

        URI mainUri = topUri.resolve("/apod/astropix.html");
        URL mainUrl;
        try {
            mainUrl = mainUri.toURL();
        } catch (MalformedURLException e) {
            return;
        }
        Document urlDoc =  getURLDocument(mainUrl);

        if (urlDoc == null) {
            throw new RetryException();
        }

        /* TODO code below should go to a separate method/class */

        /* start parsing page */
        Element body = urlDoc.body();

        /* to get image URI, look for the anchor just above it */
        Element firstCenterTag = body.child(0);
        Element secondPTag = firstCenterTag.child(2);
        Elements anchors = secondPTag.getElementsByTag("a");
        if (anchors.isEmpty()) {
            /* probably a video or something fancy */
            throw new RetryException();
        }

        /* get the image uri */
        Element imgAnchor = anchors.last();
        Element img = imgAnchor.getElementsByTag("img").first();
        URI bigImageUri = topUri.resolve("/apod/" + img.attr("src"));
        String uri = bigImageUri.toString();

        /* get title */
        Element secondCenterTag = body.child(1);
        Element titleElem = secondCenterTag.child(0);
        String title = titleElem.text();

        /* get byline */
        String secondCenterText = secondCenterTag.text();
        /* byline: everything after 'title' above */
        int idx = secondCenterText.lastIndexOf(title) + title.length();
        String byline = secondCenterText.substring(idx).trim();

        /* figure out the permanent link */
        /* TODO it would be safer to do this looking at the next/previous
         * anchors in the page...
         */
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
        String date = sdf.format(new Date());
        String link = "http://apod.nasa.gov/apod/ap" + date + ".html";

        publishArtwork(new Artwork.Builder()
            .title(title)
            .byline(byline)
            .imageUri(Uri.parse(uri))
            .token(title)
            .viewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            .build());
        scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
    }
}

