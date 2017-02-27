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

package ie.macinnes.tvheadend;

public class TvhMappings {
    private static final int[] mSampleRates = new int[]{
        96_000, 88_200, 64_000, 48_000,
        44_100, 32_000, 24_000, 22_050,
        16_000, 12_000, 11_025,  8_000,
         7_350,      0,      0,      0
    };

    public static final int sriToRate(int sri)
    {
        return mSampleRates[sri & 0xf];
    }
}
