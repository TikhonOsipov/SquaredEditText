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

    private static final int CELLS_NUMBER_DEFAULT = 8;

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
     */
    private int maxTextLength = CELLS_NUMBER_DEFAULT;
    private int cellsNumber = CELLS_NUMBER_DEFAULT;
    private int intervalQuantity = CELLS_NUMBER_DEFAULT - 1;

    private float squareWithInterval;
    private float squareWidth;
    private float squareInterval;
    private float textMargin;
    private float textHeight;
    private float textSize;
    private float squareIntervalPart;

    Paint backgroundPaint = new Paint();
    Paint textPaint = new Paint();
    Paint cursorPaint = new Paint();

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
     * Checks that EditText is empty
     * @return true if empty, false otherwise
     */
    private boolean isEmpty() {
        return text.isEmpty();
    }

    /**
     * Checks that EditText has any text
     * @return true if has text, false otherwise
     */
    private boolean hasText() {
        return text.length() > 0;
    }

    /**
     * Checks that EditText is not filled
     * @return true if not filled, false otherwise
     */
    private boolean isNotFull() {
        return text.length() < maxTextLength;
    }

    /**
     * Checks that EditText is filled with symbols
     * @return true if filled, false otherwise
     */
    private boolean isFull() {
        return text.length() == maxTextLength;
    }

    /**
     * Read custom attributes values
     * custom attribute is cellsNumber that defines number of cells
     * in background of EditText
     */
    private void readAttrs(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.LoginEditText, 0, 0);
        try {
            cellsNumber = ta.getInt(R.styleable.LoginEditText_cellsNumber,
                    CELLS_NUMBER_DEFAULT);
            maxTextLength = cellsNumber;
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
            this.maxTextLength = number;
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

        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setAntiAlias(true);

        textPaint.setColor(getResources().getColor(R.color.white));
        textPaint.setAntiAlias(true);

        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setColor(getResources().getColor(R.color.white));
        cursorPaint.setStrokeWidth(Utils.dpToPx(STROKE_WIDTH_CURSOR_DP, getContext()));
        cursorPaint.setAntiAlias(true);

        invalidate();
    }

    private void drawBackground(Canvas canvas) {
        backgroundPaint.setColor(getResources().getColor(R.color.white));
        backgroundPaint.setStrokeWidth(Utils.dpToPx(STROKE_WIDTH_SQUARE_DP, getContext()));
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
        for(int i = 0; i < maxTextLength; i++) {
            xTo = xFrom + squareWidth;
            canvas.drawRect(xFrom, one, xTo, one+squareWidth, backgroundPaint);
            xFrom = xTo + squareInterval;
        }
    }

    private void drawText(Canvas canvas) {
        if(hasText()) {
            canvas.drawText(text, 0, 1, textMargin, textHeight, textPaint);
            for (int i = 1; i < text.length(); i++) {
                canvas.drawText(text, i, i + 1, textMargin + i*(squareWithInterval + squareIntervalPart), textHeight, textPaint);
            }
            if(isNotFull()) {
                drawCursor(canvas, text.length());
            } else if(isFull() && needCursorAtTheEnd) {
                drawCursor(canvas, text.length() - 1);
            }
        }
        else if(isEmpty()) {
            drawCursor(canvas, text.length());
        }
    }

    private void drawCursor(Canvas canvas, int position) {
        float xStart;
        if(modeReplaceDigitInCursorWhenTyping) {
            if (hasText() && isNotFull()) {
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
        canvas.drawRect(xStart, yTop, xEnd, yBottom, cursorPaint);
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
            if(isFull()) {
                requestFocus();
                drawCursorAtTheEnd();

                modeReplaceDigitInCursorWhenTyping = true;
                removeTextChangedListener(textWatcher);
                //save penultimate digit of text to use it in deleting
                penultimateDigit = String.valueOf(text.charAt(text.length() - 2));
                Log.d("myLogs", "penultimateDigit (dispatchSetActivated) : " + penultimateDigit);
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
        Log.d("myLogs", "Typing finished, text = " + getText().toString());
    }

    boolean modeDelete = false;

    /**
     * TextWatcher for notifying of entering text
     */
    class CredentialsTextWatcher implements TextWatcher {
        String savedText = "";
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            //typing
            if(before == 0 && count == 1) {
                if(shouldMoveCursorOnLastDigitWhenDeleting) {
                    modeDelete = false;
                }
                if(modeReplaceDigitInCursorWhenTyping) {
                    StringBuilder sb = new StringBuilder(s);
                    //to replace digit in highlighted square
                    if(text.length() == 1) {
                        sb.replace(0, sb.length(), sb.substring(1, sb.length()));
                    } else {
                        if(modeDelete) {
                            sb.replace(sb.length() - 2, sb.length() - 1, sb.substring(sb.length() - 1));
                            sb.deleteCharAt(sb.length() - 1);
                            modeDelete = false;
                        }
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
                if(isFull()) {
                    typingFinishedListener.onTypingFinished();
                }
                savedText = text;
                modeDelete = false;
            }

            //deleting
            if(before == 1 && count == 0) {
                if(!modeDelete && isNotFull()) {
                    Log.d("myLogs", "s = " + String.valueOf(s) + ", text = " + text + ", savedText = " + savedText);
                    text = savedText;
                    setText(savedText);
                    setSelection(savedText.length());
                    modeDelete = true;
                    modeReplaceDigitInCursorWhenTyping = true;
                    return;
                }
                text = String.valueOf(s);
                if(shouldMoveCursorOnLastDigitWhenDeleting) {
                    modeDelete = true;

                    if((text + penultimateDigit).length() > 1) {
                        removeTextChangedListener(textWatcher);
                        text += penultimateDigit;
                        penultimateDigit = String.valueOf(text.charAt(text.length() - 2));
                        Log.d("myLogs", "penultimateDigit (onDelete) : " + penultimateDigit);
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

                if(isEmpty()) {
                    modeDelete = false;
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
