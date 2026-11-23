package com.nexttv;
import android.view.accessibility.AccessibilityNodeInfo;
/** Ask the current semantics owner, rather than trusting a cached pre-navigation node. */
final class FreshAccessibilityNodes {
 private FreshAccessibilityNodes(){}
 static AccessibilityNodeInfo focused(AccessibilityNodeInfo node){
  if(node==null||!node.refresh())return null;
  if(node.isFocused())return node;
  for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=focused(node.getChild(i));if(found!=null)return found;}
  return null;
 }
 static String text(AccessibilityNodeInfo node){
  if(node==null||!node.refresh())return "";
  StringBuilder value=new StringBuilder();if(node.getText()!=null)value.append(node.getText());value.append('\n');
  for(int i=0;i<node.getChildCount();i++)value.append(text(node.getChild(i)));
  return value.toString();
 }
}
