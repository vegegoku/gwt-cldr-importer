package org.gwtproject.tools.cldr;

import com.squareup.javapoet.CodeBlock;
import org.gwtproject.i18n.shared.cldr.CurrencyDataImpl;

import java.util.regex.Pattern;

/**
 * Immutable collection of data about a currency in a locale, built from the
 * CurrencyData and CurrencyExtra properties files.
 * <p>
 * This class is copy/paste from gwt com.google.gwt.i18n.rebind.CurrencyListGenerator.
 */
public class CurrencyInfo {

    private static final Pattern SPLIT_VERTICALBAR = Pattern.compile("\\|");

    private final String code;

    private final String displayName;

    private final int flags;

    private final boolean obsolete;

    private final String portableSymbol;

    private String simpleSymbol;

    private final String symbol;

    /**
     * Create an instance.
     * <p>
     * currencyData format:
     *
     * <pre>
     *       display name|symbol|decimal digits|not-used-flag
     * </pre>
     *
     * <ul>
     * <li>If a symbol is not supplied, the currency code will be used
     * <li>If # of decimal digits is omitted, 2 is used
     * <li>If a currency is not generally used, not-used-flag=1
     * <li>Trailing empty fields can be omitted
     * <li>If null, use currencyCode as the display name
     * </ul>
     * <p>
     * extraData format:
     *
     * <pre>
     *       portable symbol|flags|currency symbol override
     *     flags are space separated list of:
     *       At most one of the following:
     *         SymPrefix     The currency symbol goes before the number,
     *                       regardless of the normal position for this locale.
     *         SymSuffix     The currency symbol goes after the number,
     *                       regardless of the normal position for this locale.
     *
     *       At most one of the following:
     *         ForceSpace    Always add a space between the currency symbol
     *                       and the number.
     *         ForceNoSpace  Never add a space between the currency symbol
     *                       and the number.
     * </pre>
     *
     * @param currencyCode ISO4217 currency code
     * @param currencyData entry from a CurrencyData properties file
     * @param extraData    entry from a CurrencyExtra properties file
     * @throws NumberFormatException
     */
    public CurrencyInfo(String currencyCode, String currencyData,
                        String extraData) throws NumberFormatException {
        code = currencyCode;
        if (currencyData == null) {
            currencyData = currencyCode;
        }
        String[] currencySplit = SPLIT_VERTICALBAR.split(currencyData);
        String currencyDisplay = currencySplit[0];
        String currencySymbol = null;
        String simpleCurrencySymbol = null;
        if (currencySplit.length > 1 && currencySplit[1].length() > 0) {
            currencySymbol = currencySplit[1];
        }
        int currencyFractionDigits = 2;
        if (currencySplit.length > 2 && currencySplit[2].length() > 0) {
            currencyFractionDigits = Integer.valueOf(currencySplit[2]);
        }
        boolean currencyObsolete = false;
        if (currencySplit.length > 3 && currencySplit[3].length() > 0) {
            currencyObsolete = Integer.valueOf(currencySplit[3]) != 0;
        }
        int currencyFlags = currencyFractionDigits;
        if (currencyObsolete) {
            currencyFlags |= CurrencyDataImpl.DEPRECATED_FLAG;
        }
        String currencyPortableSymbol = "";
        if (extraData != null) {
            // CurrencyExtra contains up to 3 fields separated by |
            // 0 - portable currency symbol
            // 1 - space-separated flags regarding currency symbol
            // positioning/spacing
            // 2 - override of CLDR-derived currency symbol
            // 3 - simple currency symbol
            String[] extraSplit = SPLIT_VERTICALBAR.split(extraData);
            currencyPortableSymbol = extraSplit[0];
            if (extraSplit.length > 1) {
                if (extraSplit[1].contains("SymPrefix")) {
                    currencyFlags |= CurrencyDataImpl.POS_FIXED_FLAG;
                } else if (extraSplit[1].contains("SymSuffix")) {
                    currencyFlags |= CurrencyDataImpl.POS_FIXED_FLAG
                            | CurrencyDataImpl.POS_SUFFIX_FLAG;
                }
                if (extraSplit[1].contains("ForceSpace")) {
                    currencyFlags |= CurrencyDataImpl.SPACING_FIXED_FLAG
                            | CurrencyDataImpl.SPACE_FORCED_FLAG;
                } else if (extraSplit[1].contains("ForceNoSpace")) {
                    currencyFlags |= CurrencyDataImpl.SPACING_FIXED_FLAG;
                }
            }
            // If a non-empty override is supplied, use it for the currency
            // symbol.
            if (extraSplit.length > 2 && extraSplit[2].length() > 0) {
                currencySymbol = extraSplit[2];
            }
            // If a non-empty simple symbol is supplied, use it for the currency
            // symbol.
            if (extraSplit.length > 3 && extraSplit[3].length() > 0) {
                simpleCurrencySymbol = extraSplit[3];
            }
            // If we don't have a currency symbol yet, use the portable symbol if
            // supplied.
            if (currencySymbol == null && currencyPortableSymbol.length() > 0) {
                currencySymbol = currencyPortableSymbol;
            }
        }
        // If all else fails, use the currency code as the symbol.
        if (currencySymbol == null) {
            currencySymbol = currencyCode;
        }
        if (currencyPortableSymbol.length() == 0) {
            currencyPortableSymbol = currencySymbol;
        }
        if (simpleCurrencySymbol == null) {
            simpleCurrencySymbol = currencySymbol;
        }
        displayName = currencyDisplay;
        symbol = currencySymbol;
        flags = currencyFlags;
        portableSymbol = currencyPortableSymbol;
        simpleSymbol = simpleCurrencySymbol;
        obsolete = currencyObsolete;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CodeBlock getDefaultImplCode() {
        return CodeBlock.builder()
                .addStatement("return new $T($S,$S,$L,$S,$S)", CurrencyDataImpl.class, code, symbol, flags, portableSymbol, simpleSymbol)
                .build();
    }

    public String getJson() {
        StringBuilder buf = new StringBuilder();
        buf.append("[ \"").append(quote(code)).append("\", \"");
        buf.append(quote(symbol)).append("\", ").append(flags);
        buf.append(", \"").append(quote(portableSymbol)).append('\"');
        buf.append(", \"").append(quote(simpleSymbol)).append('\"');
        return buf.append(']').toString();
    }

    /**
     * Backslash-escape any double quotes in the supplied string.
     *
     * @param str string to quote
     * @return string with double quotes backslash-escaped.
     */
    private String quote(String str) {
        return str.replace("\"", "\\\"");
    }

    public CodeBlock asMapEntry() {
        return CodeBlock.builder()
                .addStatement("result.put($S, new $T($S,$S,$L,$S,$S))", code, CurrencyDataImpl.class, code, symbol, flags, portableSymbol, simpleSymbol)
                .build();
    }
}