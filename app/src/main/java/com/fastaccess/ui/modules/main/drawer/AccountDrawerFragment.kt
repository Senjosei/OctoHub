package com.fastaccess.ui.modules.main.drawer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.fastaccess.R
import com.fastaccess.data.entity.Login
import com.fastaccess.data.entity.PinnedRepos
import com.fastaccess.data.entity.dao.LoginDao
import com.fastaccess.data.entity.dao.PinnedReposDao
import com.fastaccess.helper.PrefGetter
import com.fastaccess.helper.RxHelper
import com.fastaccess.provider.scheme.SchemeParser
import com.fastaccess.ui.adapter.LoginAdapter
import com.fastaccess.ui.adapter.PinnedReposAdapter
import com.fastaccess.ui.base.BaseActivity
import com.fastaccess.ui.base.BaseFragment
import com.fastaccess.ui.base.adapter.BaseViewHolder
import com.fastaccess.ui.base.mvp.presenter.BasePresenter
import com.fastaccess.ui.modules.login.chooser.LoginChooserActivity
import com.fastaccess.ui.modules.main.MainMvp
import com.fastaccess.ui.modules.pinned.PinnedReposActivity
import com.fastaccess.ui.modules.user.UserPagerActivity
import com.fastaccess.ui.widgets.recyclerview.DynamicRecyclerView

/**
 * Created by Kosh on 25.03.18.
 */
class AccountDrawerFragment : BaseFragment<MainMvp.View, BasePresenter<MainMvp.View>>(),
    BaseViewHolder.OnItemClickListener<PinnedRepos> {

    private val pinnedListAdapter = PinnedReposAdapter(true)
    private val adapter = LoginAdapter(true)
    private val userModel by lazy { LoginDao.getUser().blockingGet().or() }
    private lateinit var pinnedList: DynamicRecyclerView
    private lateinit var accLists: DynamicRecyclerView
    private lateinit var logout: FrameLayout
    private lateinit var togglePinned: FrameLayout
    private lateinit var addAccLayout: FrameLayout
    private lateinit var repos: FrameLayout
    private lateinit var starred: FrameLayout
    private lateinit var toggleAccountsLayout: LinearLayout

    override fun fragmentLayout() = R.layout.accounts_menu_layout

    override fun providePresenter() = BasePresenter<MainMvp.View>()

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        pinnedList = view.findViewById(R.id.pinnedList)
        accLists = view.findViewById(R.id.accLists)
        logout = view.findViewById(R.id.logout)
        togglePinned = view.findViewById(R.id.togglePinned)
        addAccLayout = view.findViewById(R.id.addAccLayout)
        repos = view.findViewById(R.id.repos)
        toggleAccountsLayout = view.findViewById(R.id.toggleAccountsLayout)
        starred = view.findViewById(R.id.starred)

        pinnedListAdapter.listener = this
        pinnedList.adapter = pinnedListAdapter
        adapter.listener = object : BaseViewHolder.OnItemClickListener<Login> {
            override fun onItemLongClick(position: Int, v: View?, item: Login) {}
            override fun onItemClick(position: Int, v: View?, item: Login) {
                presenter.manageViewDisposable(RxHelper.getObservable(
                    LoginDao.onMultipleLogin(
                        item,
                        item.isEnterprise,
                        false
                    ).toObservable()
                )
                    .doOnSubscribe { showProgress(0) }
                    .doOnComplete { hideProgress() }
                    .subscribe(
                        { (activity as? BaseActivity<*, *>?)?.onRestartApp() },
                        ::println
                    )
                )
            }
        }
        accLists.adapter = adapter

        logout.setOnClickListener {
            postDelayedAndClose {
                activity?.let {
                    (it as? BaseActivity<*, *>)?.onLogoutPressed()
                }
            }
        }
        togglePinned.setOnClickListener {
            postDelayedAndClose { PinnedReposActivity.startActivity(it.context) }
        }
        addAccLayout.setOnClickListener {
            postDelayedAndClose {
                val intent = Intent(it.context, LoginChooserActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
            }
        }
        repos.setOnClickListener {
            postDelayedAndClose {
                UserPagerActivity.startActivity(
                    it.context,
                    userModel.login!!,
                    false,
                    PrefGetter.isEnterprise,
                    3
                )
            }
        }
        starred.setOnClickListener {
            postDelayedAndClose {
                UserPagerActivity.startActivity(
                    it.context,
                    userModel.login!!,
                    false,
                    PrefGetter.isEnterprise,
                    4
                )
            }
        }

        loadAccount()
        loadPinned()
    }

    override fun onItemClick(position: Int, v: View?, item: PinnedRepos) {
        if (v != null) {
            postDelayedAndClose { SchemeParser.launchUri(v.context, item.pinnedRepo!!.htmlUrl!!) }
        }
    }

    override fun onItemLongClick(position: Int, v: View?, item: PinnedRepos) = Unit

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
            loadPinned()
        }
    }

    private fun loadAccount() {
        presenter.manageViewDisposable(
            RxHelper.getObservable(
                LoginDao.getAccounts().toObservable()
            )
                .doOnComplete {
                    if (!adapter.isEmpty) {
                        toggleAccountsLayout.visibility = View.VISIBLE
                    } else {
                        toggleAccountsLayout.visibility = View.GONE
                    }
                }
                .toList()
                .subscribe({
                    adapter.addItems(it)
                }, ::print)
        )
    }

    private fun loadPinned() {
        presenter?.manageViewDisposable(
            RxHelper.getObservable(
                PinnedReposDao.getMenuRepos()
            )
                .subscribe({ pinnedListAdapter.insertItems(it) }, ::println)
        )
    }

    private fun closeDrawer() {
        val activity = activity as? BaseActivity<*, *>? ?: return
        activity.closeDrawer()
    }

    private fun postDelayedAndClose(method: () -> Unit) {
        closeDrawer()
        view?.postDelayed({ method.invoke() }, 250)
    }
}
