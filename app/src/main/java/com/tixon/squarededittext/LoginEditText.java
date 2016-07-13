package com.tixon.squarededittext;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Created by tikhon.osipov on 08.07.2016
 *
 * @author tikhon.osipov
 *
 * This EditText has custom behavior and background. It consists of several cells and a cursor
 * which are drawn on the background in the onDraw method.
 * Number of cells is 8 by default, but this number can be changed in custom attribute
 * @see #readAttrs(Context, AttributeSet)
 * or by calling a method
 * @see #setCellsNumber(int)
 *
 * The initial state of EditText: text is empty, cursor is at the first position.
 * When user starts to type any symbol, it is placing into cell selected with a cursor, and
 * cursor is moving to the next cell. When the last cell is selected with cursor and empty,
 * and user types any symbol, it is placing into this cell, and cursor disappears;
 * @see #onTypingFinished() is running which tells that user has filled all the cells.
 * @see TypingFinishedListener
 *
 * This EditText dispatches its activation in method when somewhere is called method
 * "editText.activate()"
 * EditText can become activated only if all the cells are filled with symbols.
 * @see #dispatchSetActivated(boolean)
 *
 * When user activates EditText, the last cell becomes selected with a cursor using
 * @see #needCursorAtTheEnd,
 * and boolean flags
 * @see #modeReplaceDigitInCursorWhenTyping
 * @see #shouldMoveCursorOnLastDigitWhenDeleting
 * become true. So behavior of EditText changes. While there are any symbols in EditText
 * cells, when user types any symbol, symbol in selected cell by cursor is replaced by
 * symbol which was typed by user. Boolean flags become false and previous behavior returns.
 * Also when user deletes symbols, cursor selects the last symbol in EditText. Flags don't change
 * their state.
 * When user deletes all symbols in EditText, flags become false and EditText takes the initial
 * state.
 */
public class LoginEditText extends EditText implements TypingFinishedListener {

    private TypingFinishedListener typingFinishedListener;

    public void setTypingFinishedListener(TypingFinishedListener listener) {
        this.typingFinishedListener = listener;
    }

    private static final int SQUARE_QUANTITY_DEFAULT = 8;

    //Size in dp from design
    private static final int TEXT_SIZE = 14;
    private static final int TEXT_LEFT_MARGIN = 9;
    private static final int TEXT_HEIGHT = 19;
    private static final int SQUARE_WIDTH = 26;

    //percentage: interval between squares 13%, square 87% (of square with interval,
    // which is EditText.width() / number of squares)
    private static final float INTERVAL_PERCENTAGE = 0.13f;
    private static final float SQUARE_PERCENTAGE = 0.87f;
    public static final float STROKE_MARGIN_DP = 2.0f;
    public static final float STROKE_WIDTH_SQUARE_DP = 1.0f;
    public static final float STROKE_WIDTH_CURSOR_DP = 3.0f;

    /**
     * cellsNumber is read from custom attributes from LoginEditText
     * default value is 8
     * custom value is used in res/layout/activity_login_second_layer.xml
     */
    private int userIdLength = 8;
    private int cellsNumber = 8;
    private int intervalQuantity = 7;

    private float squareWithInterval;
    private float squareWidth;
    private float squareInterval;
    private float textMargin;
    private float textHeight;
    private float textSize;
    private float squareIntervalPart;

    Paint paint = new Paint();
    Paint textPaint = new Paint();
    Paint squareCursorPaint = new Paint();

    private String text = "";
    private String penultimateDigit = "";

    private boolean needCursorAtTheEnd = false;
    private boolean shouldMoveCursorOnLastDigitWhenDeleting = false;
    private boolean modeReplaceDigitInCursorWhenTyping = false;

    CredentialsTextWatcher textWatcher;

    public LoginEditText(Context context) {
        super(context);
        init();
    }

    public LoginEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        readAttrs(context, attrs);
        init();
    }

    public LoginEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        readAttrs(context, attrs);
        init();
    }

    /**
     * Read custom attributes values
     * custom attribute is cellsNumber that defines number of squares
     * in background of EditText
     */
    private void readAttrs(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.LoginEditText, 0, 0);
        try {
            cellsNumber = ta.getInt(R.styleable.LoginEditText_cellsNumber,
                    SQUARE_QUANTITY_DEFAULT);
            userIdLength = cellsNumber;
            intervalQuantity = cellsNumber - 1;
        } finally {
            ta.recycle();
        }
    }

    /**
     * Set number of cells programmatically
     * @param number number of cells
     */
    @SuppressWarnings("unused")
    public void setCellsNumber(int number) {
        if(number > 0) {
            this.cellsNumber = number;
            this.intervalQuantity = number - 1;
        }
    }

    void init() {
        setTypingFinishedListener(this);
        textWatcher = new CredentialsTextWatcher();
        addTextChangedListener(textWatcher);

        disableActionMode();
        disableEnterPress();
        disableActionDone();

        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);

        textPaint.setColor(getResources().getColor(R.color.white));
        textPaint.setAntiAlias(true);

        squareCursorPaint.setStyle(Paint.Style.STROKE);
        squareCursorPaint.setColor(getResources().getColor(R.color.white));
        squareCursorPaint.setStrokeWidth(Utils.dpToPx(STROKE_WIDTH_CURSOR_DP, getContext()));
        squareCursorPaint.setAntiAlias(true);

        invalidate();
    }

    private void drawBackground(Canvas canvas) {
        paint.setColor(getResources().getColor(R.color.white));
        paint.setStrokeWidth(Utils.dpToPx(STROKE_WIDTH_SQUARE_DP, getContext()));
        calculateSizes();
        textPaint.setTextSize(textSize);
        drawSquares(canvas);
    }

    /**
     * Calculate sizes for background
     */
    private void calculateSizes() {
        squareWithInterval = (float) getWidth() / (float) cellsNumber;
        squareIntervalPart = (squareWithInterval * INTERVAL_PERCENTAGE) / intervalQuantity / 2.0f;
        // \/2.0f here because without it last square is of out of borders
        squareInterval = squareWithInterval * INTERVAL_PERCENTAGE + squareIntervalPart;
        squareWidth = squareWithInterval * SQUARE_PERCENTAGE;
        textMargin = squareWithInterval * ((float)TEXT_LEFT_MARGIN / (float)SQUARE_WIDTH);
        textHeight = squareWidth * ((float)TEXT_HEIGHT / (float)SQUARE_WIDTH);
        textSize = squareWidth * ((float)TEXT_SIZE / (float)SQUARE_WIDTH);
    }

    /**
     * draw squares for background
     */
    private void drawSquares(Canvas canvas) {
        float xFrom = Utils.dpToPx(STROKE_WIDTH_SQUARE_DP, getContext());
        float one = xFrom;
        float xTo;
        for(int i = 0; i < userIdLength; i++) {
            xTo = xFrom + squareWidth;
            canvas.drawRect(xFrom, one, xTo, one+squareWidth, paint);
            xFrom = xTo + squareInterval;
        }
    }

    private void drawText(Canvas canvas) {
        if(text.length() >= 1) {
            canvas.drawText(text, 0, 1, textMargin, textHeight, textPaint);
            for (int i = 1; i < text.length(); i++) {
                canvas.drawText(text, i, i + 1, textMargin + i*(squareWithInterval + squareIntervalPart), textHeight, textPaint);
            }
            if(text.length() < userIdLength) {
                drawCursor(canvas, text.length());
            } else if(text.length() == userIdLength && needCursorAtTheEnd) {
                drawCursor(canvas, text.length() - 1);
            }
        }
        else if(text.isEmpty()) {
            drawCursor(canvas, text.length());
        }
    }

    private void drawCursor(Canvas canvas, int position) {
        float xStart;
        if(modeReplaceDigitInCursorWhenTyping) {
            if (text.length() > 0 && text.length() < 8) {
                xStart = (position - 1) * (squareWithInterval + squareIntervalPart) + Utils.dpToPx(STROKE_MARGIN_DP, getContext());
            } else {
                xStart = position * (squareWithInterval + squareIntervalPart) + Utils.dpToPx(STROKE_MARGIN_DP, getContext());
            }
        } else {
            xStart = position * (squareWithInterval + squareIntervalPart) + Utils.dpToPx(STROKE_MARGIN_DP, getContext());
        }
        float xEnd = xStart + squareWidth - Utils.dpToPx(STROKE_MARGIN_DP, getContext());
        float yTop = Utils.dpToPx(STROKE_MARGIN_DP, getContext());
        float yBottom = squareWidth;
        canvas.drawRect(xStart, yTop, xEnd, yBottom, squareCursorPaint);
    }

    public void drawCursorAtTheEnd() {
        needCursorAtTheEnd = true;
        invalidate();
    }

    public void clearCursorAtTheEnd() {
        needCursorAtTheEnd = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);
        drawText(canvas);
    }

    // Override methods

    @Override
    protected void dispatchSetActivated(boolean activated) {
        super.dispatchSetActivated(activated);
        if(activated) {
            if(text.length() == 8) {
                requestFocus();
                drawCursorAtTheEnd();

                modeReplaceDigitInCursorWhenTyping = true;
                removeTextChangedListener(textWatcher);
                //save penultimate digit of text to use it in deleting
                penultimateDigit = String.valueOf(text.charAt(text.length() - 2));
                shouldMoveCursorOnLastDigitWhenDeleting = true;

                setText(text.substring(0, text.length() - 1));
                addTextChangedListener(textWatcher);
            }
        }
        Log.d("myLogs", "setActivated = " + activated);
    }

    /**
     * When typed all digits
     */
    @Override
    public void onTypingFinished() {
        clearCursorAtTheEnd();
        clearFocus();
        setActivated(false);
        Log.d("myLogs", getText().toString());
    }

    /**
     * TextWatcher for notifying of entering text
     */
    class CredentialsTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            //typing
            if(before == 0 && count == 1) {
                if(modeReplaceDigitInCursorWhenTyping) {
                    StringBuilder sb = new StringBuilder(s);
                    //to replace digit in highlighted square
                    if(text.length() == 1) {
                        sb.replace(0, sb.length(), sb.substring(1, sb.length()));
                    }
                    text = sb.toString();
                    setText(text);
                    invalidate();
                    modeReplaceDigitInCursorWhenTyping = false;
                    shouldMoveCursorOnLastDigitWhenDeleting = false;
                } else {
                    text = String.valueOf(s);
                    invalidate();
                }
                if(text.length() == 8) {
                    typingFinishedListener.onTypingFinished();
                }
            }

            //deleting
            if(before == 1 && count == 0) {
                text = String.valueOf(s);
                if(shouldMoveCursorOnLastDigitWhenDeleting) {
                    Log.d("myLogs", "s = " + s + "; text + penultimate = " + (text+penultimateDigit) + "; text + penultimate length: " + (text+penultimateDigit).length() + "; penultimate = " + penultimateDigit);
                    if((text + penultimateDigit).length() > 1) {
                        removeTextChangedListener(textWatcher);
                        text += penultimateDigit;
                        penultimateDigit = String.valueOf(text.charAt(text.length() - 2));
                        addTextChangedListener(textWatcher);
                    } else if((text + penultimateDigit).length() == 1) {
                        removeTextChangedListener(textWatcher);
                        text = penultimateDigit;
                        setText(text);
                        penultimateDigit = "";
                        addTextChangedListener(textWatcher);
                    }
                }

                if((text + penultimateDigit).length() == 0) {
                    Log.d("myLogs", "clear selection");
                    modeReplaceDigitInCursorWhenTyping = false;
                    shouldMoveCursorOnLastDigitWhenDeleting = false;
                }
                invalidate();
            }
        }
        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    //disables

    /**
     * Disable selection change
     */
    @Override
    public void onSelectionChanged(int start, int end) {
        CharSequence text = getText();
        if (text != null) {
            if (start != text.length() || end != text.length()) {
                setSelection(text.length(), text.length());
                return;
            }
        }
        super.onSelectionChanged(start, end);
    }

    /**
     * Disable action of "done" button on keyboard
     */
    private void disableActionDone() {
        setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId == EditorInfo.IME_ACTION_DONE) {
                    //do nothing
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Disable action of "enter" button on keyboard
     */
    private void disableEnterPress() {
        setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if(keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    //do nothing
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Disable long click on EditText, calling ActionMode and copy/paste
     */
    private void disableActionMode() {
        setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }
}
