package org.h2.index;

import org.h2.expression.Expression;
import org.h2.expression.ExpressionColumn;
import org.h2.expression.ValueExpression;
import org.h2.expression.aggregate.Aggregate;
import org.h2.expression.aggregate.AggregateType;
import org.h2.expression.condition.Comparison;
import org.h2.expression.condition.ConditionAndOr;
import org.h2.expression.condition.ConditionAndOrN;
import org.h2.mvstore.tx.TransactionMap;
import org.h2.result.SearchRow;
import org.h2.table.Column;
import org.h2.table.Table;
import org.h2.value.Value;
import org.h2.value.ValueBigint;

import java.util.*;

public class IndexHandler {
    private static String hashBitIndexName  = "hashBitIndex";
    private static int[] outerAndOrTempBitmapArray = new int[]{1, 1, 1, 1, 0, 0, 0, 1};

    private static ArrayList<Integer> getBitMapIndices(Column column, Table table) {
        ArrayList<Integer> integerArray = new ArrayList<>(outerAndOrTempBitmapArray.length);
        for (int j : outerAndOrTempBitmapArray) {
            integerArray.add(j);
        }
        return integerArray;
    }

    //TODO: When there is another funcs with count, there can be errors so handle these (Expression == 1) things or Do count separtly and remove that expression
    public static ArrayList<Integer> comparisonOperationIndexes(Expression expression) {
        int i = 0;
        Column column;
        String columnName;
        Table table;
        Index columnIndex;
        Comparison comparison;
        ArrayList<Integer> andOrExpressions = new ArrayList<>();
        if (expression instanceof Comparison) {
            comparison = (Comparison) expression;
            //TODO Implement this for nested values
            if (comparison.getLeft() instanceof ExpressionColumn) {
                column = ((ExpressionColumn)(comparison.getLeft())).getColumn();
                columnName = column.getName();
                table = column.getTable();
                columnIndex = table.getIndexForColumn(column, false, false);
                if (comparison.getRight() instanceof ValueExpression &&
                        (comparison.getCompareType() ==  Comparison.EQUAL) &&
                        (true || columnIndex.indexType.equals(hashBitIndexName) &&
                                columnIndex.indexColumns.length == 1)) {
                    //Get Bitmap Indices
                    return getBitMapIndices(column, table);
                }
            }
        }
        return null;
    }

    //TODO: When there is another funcs with AND/OR, there can be errors so handle these (Expression == 1) things or Do count separtly and remove that expression
    public static ArrayList<Integer> andOrOperationIndexes(Expression expression) {
        ArrayList<Integer> resultBitmap = new ArrayList<>(), left = null, right = null, results = null, values = null,
                tempresults = null;
        ConditionAndOr conditionAndOr;
        ConditionAndOrN conditionAndOrn;
        Expression ex;
        if (expression instanceof Comparison) {
            results = comparisonOperationIndexes(expression);
        } else if (expression instanceof ConditionAndOr) {
            conditionAndOr = (ConditionAndOr) expression;
            left = andOrOperationIndexes(conditionAndOr.getLeft());
            right = andOrOperationIndexes(conditionAndOr.getRight());
            if (left != null && right != null) {
                results = andOrBitMap(left, right, conditionAndOr.getAndOrType());
            }
        } else if (expression instanceof ConditionAndOrN) {
            conditionAndOrn = (ConditionAndOrN) expression;
            for (int i = 0; i < conditionAndOrn.getSubexpressionCount(); i++) {
                ex = conditionAndOrn.getSubexpression(i);
                tempresults = andOrOperationIndexes(ex);
                if (tempresults != null) {
                    if (results == null) {
                        results = tempresults;
                    } else {
                        results = andOrBitMap(results, tempresults, conditionAndOrn.getAndOrType());
                    }
                } else {
                    return null;
                }
            }
        }
        return results;
    }

    public static ArrayList<Integer> andOrBitMap(ArrayList<Integer> left, ArrayList<Integer> right, int type) {
        ArrayList<Integer> results = new ArrayList<>();
        for(int i = 0; i < left.size(); i++) {
            if (type == ConditionAndOr.OR) {
                results.add(Math.min(1, left.get(i) + right.get(i)));
            } else if (type == ConditionAndOr.AND) {
                results.add(left.get(i) * right.get(i));
            }
        }
        return results;
    }

    public String getHashBitIndexName() {
        return hashBitIndexName;
    }

    public void setHashBitIndexName(String hashBitIndexName) {
        this.hashBitIndexName = hashBitIndexName;
    }
}