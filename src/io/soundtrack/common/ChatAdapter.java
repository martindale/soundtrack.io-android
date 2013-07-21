package io.soundtrack.common;

import java.util.ArrayList;

import io.soundtrack.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class ChatAdapter extends BaseAdapter
{

	ArrayList<ChatItem> chatItems;

	public ChatAdapter(ArrayList<ChatItem> chats)
	{
		this.chatItems = chats;
	}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		View v = convertView;

		if (v == null)
		{
			LayoutInflater vi = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = vi.inflate(R.layout.list_item, null);
		}

		ChatItem item = (ChatItem) chatItems.get(position);

		if (item != null)
		{
			TextView chatAuthor = (TextView) v.findViewById(R.id.chatAuthor);
			TextView chatMessage = (TextView) v.findViewById(R.id.chatMessage);

			if (chatAuthor != null)
			{
				// Log.d("chatAdapter", "author set to " + item.author);
				chatAuthor.setText(item.author);
			}

			if (chatMessage != null)
			{
				// Log.d("chatAdapter", "msg set to " + item.message);
				chatMessage.setText(item.message);
			}
		}
		return v;
	}

	@Override
	public int getCount()
	{
		return chatItems.size();
	}

	@Override
	public Object getItem(int position)
	{
		return chatItems.get(position);
	}

	@Override
	public long getItemId(int position)
	{
		// TODO Auto-generated method stub
		return 0;
	}
}