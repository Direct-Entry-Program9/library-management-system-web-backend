package lk.ijse.dep9.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HttpServlet2;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "BookServlet", value = "/books/*")
public class BookServlet extends HttpServlet2 {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")){
            String query = request.getParameter("q");
            String size = request.getParameter("size");
            String page = request.getParameter("page");

            if (query!=null && size!=null && page!=null){
                if (!page.matches("\\d+") || !size.matches("\\d+")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid size or page");
                }else {
                    paginatedSearchBooks(query,size,page,response);
                }
            } else if (query != null) {
                searchBooks(query,response);
            } else if (page != null && size != null) {
                if (!page.matches("\\d+") || !size.matches("\\d+")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid size or page");
                }else {
                    paginatedLoadBooks(size,page,response);
                }
            }else {
                loadAllBooks(response);
            }
        }else {
            Matcher matcher = Pattern.compile("^/(\\d{13})/?$").matcher(request.getPathInfo());
            if (matcher.matches()){
                getBookDetails(matcher.group(1),response);
            }else {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid URL");
            }
        }
    }

    private void loadAllBooks(HttpServletResponse response) throws IOException {
        response.getWriter().println("Load All Books");
    }

    private void paginatedLoadBooks(String size, String page, HttpServletResponse response) throws IOException {
        response.getWriter().println("load Books by page");
    }

    private void searchBooks(String query, HttpServletResponse response) throws IOException {
        response.getWriter().println("Search Books");
    }

    private void paginatedSearchBooks(String query, String size, String page, HttpServletResponse response) throws IOException {
        response.getWriter().println("Search books by page");
    }

    private void getBookDetails(String isbn, HttpServletResponse response) throws IOException {
        response.getWriter().println("Get Book Details");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.getWriter().println("Books: doPost()");
    }

    @Override
    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.getWriter().println("Books: doPatch()");
    }
}
