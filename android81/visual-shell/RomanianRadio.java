package ro.leo.visual;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class RomanianRadio {
    static final class Station {
        final String name, url, category, mark;
        final int color;
        final boolean builtIn;

        Station(String name, String url, String category, String mark, int color, boolean builtIn) {
            this.name = name;
            this.url = url;
            this.category = category;
            this.mark = mark;
            this.color = color;
            this.builtIn = builtIn;
        }
    }

    // Direct audio endpoints from the broadcasters' published M3U/PLS playlists.
    static final List<Station> STATIONS = Collections.unmodifiableList(Arrays.asList(
            station("România Actualități", "http://stream2.srr.ro:8008/", "Știri și muzică", "RA", 0xffdb7778),
            station("România Cultural", "http://stream2.srr.ro:8010/", "Cultură", "RC", 0xffbba1ee),
            station("România Muzical", "http://stream2.srr.ro:8020/", "Clasică", "RM", 0xffedbd71),
            station("Antena Satelor", "http://stream2.srr.ro:8040/", "Folclor", "AS", 0xff9acb8d),
            station("București FM", "http://stream2.srr.ro:8030/", "Local", "BF", 0xff68cbe0),
            station("Radio Cluj", "https://stream4.srr.ro:8443/radio-cluj", "Regional", "CJ", 0xff83b3ff),
            station("Radio Brașov FM", "http://stream2.srr.ro:8210/", "Regional", "BV", 0xff79cabc),
            station("Kiss FM", "https://live.kissfm.ro/kissfm.aacp", "Hituri", "KF", 0xffef91b9),
            station("Magic FM", "https://live.magicfm.ro/magicfm.aacp", "Pop", "MF", 0xffbd9def),
            station("Rock FM", "https://live.rockfm.ro/rockfm.aacp", "Rock", "RF", 0xfff0a17d)));

    private static Station station(String name, String url, String category, String mark, int color) {
        return new Station(name, url, category, mark, color, true);
    }

    private RomanianRadio() {}
}
