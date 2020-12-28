package edu.osu.pcv.marslogger;

import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import edu.osu.pcv.marslogger.ImuViewFragment.OnListFragmentInteractionListener;
import edu.osu.pcv.marslogger.ImuViewContent.SingleAxis;
import timber.log.Timber;

import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link SingleAxis} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 */
public class ImuRecyclerViewAdapter extends RecyclerView.Adapter<ImuRecyclerViewAdapter.ViewHolder> {
    private final List<SingleAxis> mValues;
    private final OnListFragmentInteractionListener mListener;

    public ImuRecyclerViewAdapter(List<SingleAxis> items, OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(
            R.layout.fragment_imu, parent,false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mIdView.setText(holder.mItem.id);
        holder.mContentView.setText(holder.mItem.content);
        holder.mUnitView.setText(FileHelper.fromHtml(holder.mItem.unit));
        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    mListener.onListFragmentInteraction(holder.mItem);
                }
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.fragment_imu;
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    /**
     *
     * @param position
     * @param value
     */
    public void updateListItem(int position, float value) {
        mValues.get(position).content = String.format("%.3f", value);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mIdView;
        public final TextView mContentView;
        public final TextView mUnitView;
        public SingleAxis mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mIdView = (TextView) view.findViewById(R.id.item_number);
            mContentView = (TextView) view.findViewById(R.id.content);
            mUnitView = (TextView) view.findViewById(R.id.unit);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }
}
