/**
 * Text3d.java
 * <p>
 * Copyright 2014-2016 Michael Hoffer info@michaelhoffer.de. All rights
 * reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * <p>
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY Michael Hoffer info@michaelhoffer.de "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL Michael Hoffer info@michaelhoffer.de OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of Michael Hoffer
 * info@michaelhoffer.de.
 */
package eu.mihosoft.vrl.v3d;

import java.util.ArrayList;
import java.util.List;
import java.awt.Font;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 3d text primitive.
 *
 * @author Michael Hoffer info@michaelhoffer.de
 */
public class Text3d extends Primitive {
    private final static Logger LOGGER = Logger.getLogger(Text3d.class.getName());
    private final PropertyStorage properties = new PropertyStorage();
    private final List<CSG> letters = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param text text
     */
    public Text3d(String text) {
        this(text, Font.SANS_SERIF, 12, 1.0);
    }

    /**
     * Constructor.
     *
     * @param text  text
     * @param depth text depth (z thickness)
     */
    public Text3d(String text, double depth) {
        this(text, Font.SANS_SERIF, 12, depth);
    }

    /**
     * Constructor.
     *
     * @param text     text
     * @param fontName font name, e.g., "Arial"
     * @param fontSize font size
     * @param depth    text depth (z thickness)
     */
    public Text3d(String text, String fontName, double fontSize, double depth) {
        Font font = new Font(fontName, Font.PLAIN, (int) fontSize);
        if (!font.getFamily().equalsIgnoreCase(fontName)) {
            LOGGER.log(Level.INFO, String.format("Fontname '%s' was not found, defaulting to '%s'", fontName, font.getFamily()));
        }

        List<CSG> tmp = TextExtrude.text(depth, text, font);
        letters.clear();
        for (int i = 0; i < tmp.size(); i++) {
            letters.add(tmp.get(i)
                    .rotx(180)
                    .toZMin()
            );
        }
    }

    @Override
    public List<Polygon> toPolygons() {
        List<Polygon> poly = new ArrayList<>();
        for (CSG c : letters) {
            poly.addAll(c.getPolygons());
        }
        return poly;
    }

    @Override
    public PropertyStorage getProperties() {
        return properties;
    }

    public Text3d noCenter() {
        return this;
    }

}
