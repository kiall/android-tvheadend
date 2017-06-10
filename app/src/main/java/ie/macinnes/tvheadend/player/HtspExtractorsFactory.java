/*
 * Copyright (c) 2017 Kiall Mac Innes <kiall@macinnes.ie>
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

package ie.macinnes.tvheadend.player;

import android.content.Context;

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;

public class HtspExtractorsFactory implements ExtractorsFactory {
    private static final String TAG = HtspExtractorsFactory.class.getName();

    private final Context mContext;

    public HtspExtractorsFactory(Context context) {
        mContext = context;
    }

    @Override
    public Extractor[] createExtractors() {
        Extractor[] extractors = new Extractor[2];

        extractors[0] = new HtspExtractor(mContext);
        extractors[1] = new TsExtractor(0);

        return extractors;
    }
}
