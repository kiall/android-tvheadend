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

import android.media.tv.TvContract;
import android.util.SparseArray;

public class DvbMappings {
    private DvbMappings() {
        throw new IllegalAccessError("Utility class");
    }

    public static final SparseArray<String> ProgramGenre = new SparseArray<String>() {
        {
            append(16, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(17, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(18, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(19, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(20, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.COMEDY));
            append(21, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(22, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(23, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.DRAMA));
            append(32, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(33, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(34, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(35, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(48, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(49, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(50, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(51, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ENTERTAINMENT));
            append(64, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(65, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(66, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(67, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(68, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(69, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(70, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(71, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(72, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(73, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(74, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(75, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SPORTS));
            append(80, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(81, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(82, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(82, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(83, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(84, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(85, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.FAMILY_KIDS));
            append(96, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(97, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(98, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(99, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(100, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(101, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(102, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MUSIC));
            append(112, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(113, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(114, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(115, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(116, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(117, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(118, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(118, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.MOVIES));
            append(120, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(121, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.NEWS));
            append(122, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(129, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(144, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(145, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ANIMAL_WILDLIFE));
            append(146, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(147, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(148, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TECH_SCIENCE));
            append(150, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.EDUCATION));
            append(160, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
            append(161, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.TRAVEL));
            append(162, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.ARTS));
            append(163, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
            append(164, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
            append(165, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
            append(166, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.SHOPPING));
            append(167, TvContract.Programs.Genres.encode(TvContract.Programs.Genres.LIFE_STYLE));
        }
    };
}
