/*
 * Copyright (c) 2016 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ie.macinnes.tvheadend.tvinput;

import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.Factory;

import java.util.Map;

public final class ExoPlayerHttpDataSourceFactory implements Factory {
    private Map<String, String> mHeaders;

    public ExoPlayerHttpDataSourceFactory(Map<String, String> headers) {
        mHeaders = headers;
    }

    @Override
    public DefaultHttpDataSource createDataSource() {
        DefaultHttpDataSource dataSource = new DefaultHttpDataSource("Foo", null);

        for (Map.Entry<String, String> entry : mHeaders.entrySet()) {
            dataSource.setRequestProperty(entry.getKey(), entry.getValue());
        }

        return dataSource;
    }
}
